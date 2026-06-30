import React, { useState, useEffect } from 'react';
import { StyleSheet, View } from 'react-native';
import HybridContainer from './HybridContainer';
import HybridScreen from './HybridScreen';
import { hybridNavigationEmitter, registerScreen } from './HybridNavigation';
import { screens } from './screens';

// 封装容器：
// 1. 启动时根据 screens map 自动注册所有 RN 页面到 YYPRouter
// 2. 监听 YYPRouter 的 pushRoute 事件，把 routePath 加入 activeScreens，params 解析后存到 screenParams
// 3. 用 screens map 渲染所有 HybridScreen 作为 HybridContainer 的子组件，把 params 作为 prop 注入
export default function HybridNavigationRoot({ routePath = 'default' }: { routePath?: string }) {
  const [activeScreens, setActiveScreens] = useState<Set<string>>(new Set());
  const [screenParams, setScreenParams] = useState<Record<string, any>>({});

  useEffect(() => {
    Object.entries(screens).forEach(([key, def]) => registerScreen(key, def));

    const sub = hybridNavigationEmitter.addListener(
      'YYPNavPushRN',
      (event: { routePath: string; module: string; params?: string }) => {
        setActiveScreens(prev => new Set([...prev, event.routePath]));
        if (event.params) {
          try {
            const parsed = JSON.parse(event.params);
            setScreenParams(prev => ({ ...prev, [event.routePath]: parsed }));
          } catch (e) {
            console.error('[HybridNav] Failed to parse params for', event.routePath, e);
          }
        }
      },
    );
    return () => sub.remove();
  }, []);

  const handleNativeDismiss = (screenKey: string) => {
    setActiveScreens(prev => {
      const next = new Set(prev);
      next.delete(screenKey);
      return next;
    });
  };

  return (
    <View style={styles.root} pointerEvents="box-none">
      <HybridContainer routePath={routePath}>
        {Object.entries(screens).map(([key, def]) => {
          const Screen = def.screen;
          const params = screenParams[key];
          return (
            <HybridScreen
              key={key}
              screenKey={key}
              activityMode={activeScreens.has(key) ? 'attached' : 'detached'}
              onNativeDismiss={handleNativeDismiss}>
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
