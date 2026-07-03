import React, { useState, useEffect } from 'react';
import { StyleSheet, View } from 'react-native';
import HybridContainer from './HybridContainer';
import HybridScreen from './HybridScreen';
import { hybridNavigationEmitter, registerScreen } from './HybridNavigation';
import { screens, INDEX_PATH } from './screens';

type ActiveScreen = {
  routeID: string;
  routePath: string;
  params?: any;
};

// 混合导航根容器：
// 1. 启动时按 screens map 注册所有 RN 页面到 YYPRouter（仅注册，不渲染）
// 2. 监听 YYPRouter 的 pushRoute 事件，按 routeID 加入 Map（动态渲染，按需挂载）
// 3. 监听 HybridScreen 的 onNativeDismiss，按 routeID 精确移除
// 4. 同 routePath push 两次时，routeID 区分两个实例（React key = routeID）
export default function HybridNavigationRoot() {
  const [activeScreens, setActiveScreens] = useState<Map<string, ActiveScreen>>(new Map());

  useEffect(() => {
    Object.entries(screens).forEach(([key, def]) => registerScreen(key, def));

    // 启动期主动 mount Root 页(routeID = module 名 "default")。
    // registerScreen 已同步完成,原生端 rootModules 就绪。
    // setActiveScreens 触发 re-render → Fabric mount → onEvent ScreenDidMount routePath=__index__ →
    // 原生换 window.rootViewController 为新 nav(rootVC = ScreenRoot)。
    if (screens[INDEX_PATH]) {
      setActiveScreens(prev => {
        const next = new Map(prev);
        next.set('default', { routeID: 'default', routePath: INDEX_PATH });
        return next;
      });
    }

    const sub = hybridNavigationEmitter.addListener(
      'YYPNavPushRN',
      (event: { routeID: string; routePath: string; module: string; params?: string }) => {
        if (!event.routeID) {
          console.warn('[HybridNav] YYPNavPushRN 缺少 routeID', event);
          return;
        }
        let params: any;
        if (event.params) {
          try {
            params = JSON.parse(event.params);
          } catch (e) {
            console.error('[HybridNav] Failed to parse params for', event.routePath, e);
          }
        }
        setActiveScreens(prev => {
          const next = new Map(prev);
          next.set(event.routeID, { routeID: event.routeID, routePath: event.routePath, params });
          return next;
        });
      },
    );
    return () => sub.remove();
  }, []);

  const handleNativeDismiss = (routeID: string) => {
    setActiveScreens(prev => {
      if (!prev.has(routeID)) return prev;
      const next = new Map(prev);
      next.delete(routeID);
      return next;
    });
  };

  return (
    <View style={styles.root} pointerEvents="box-none">
      <HybridContainer>
        {Array.from(activeScreens.values()).map(({ routeID, routePath, params }) => {
          const def = screens[routePath];
          if (!def) {
            console.warn('[HybridNav] 未注册的 routePath:', routePath);
            return null;
          }
          const Screen = def.screen;
          return (
            <HybridScreen key={routeID} routeID={routeID} routePath={routePath} onNativeDismiss={handleNativeDismiss}>
              <Screen params={params} />
            </HybridScreen>
          );
        })}
      </HybridContainer>
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: 'transparent',
  },
});
