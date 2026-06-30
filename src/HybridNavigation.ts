import { NativeEventEmitter, NativeModules } from 'react-native';
import { zodToJsonSchema } from 'zod-to-json-schema';
import { screens, type ScreenDefinition } from './screens';

const { YYPNavigationModule } = NativeModules;

export const hybridNavigationEmitter = new NativeEventEmitter(
  YYPNavigationModule,
);

/// 注册一个 RN 页面到 YYPRouter（platform = REACT_NATIVE）。
/// schema 通过 zod-to-json-schema 转 JSON Schema 字符串发给原生作为 metadata。
export function registerScreen(routePath: string, def: ScreenDefinition, module: string = 'default'): void {
  const schemaJSON = def.schema ? JSON.stringify(zodToJsonSchema(def.schema)) : '';
  YYPNavigationModule.registerScreen(routePath, module, schemaJSON);
}

/// 主动 push 一个页面。params 会先用 zod schema 校验（早失败），通过后序列化为 JSON 发给原生。
export function pushRoute(routePath: string, params?: any, module: string = 'default'): void {
  const def = screens[routePath];
  if (def?.schema && params !== undefined) {
    const result = def.schema.safeParse(params);
    if (!result.success) {
      console.error(`[HybridNav] Invalid params for ${routePath}:`, result.error.issues);
      return;
    }
  }
  const paramsJSON = params ? JSON.stringify(params) : '';
  YYPNavigationModule.pushRoute(routePath, module, paramsJSON);
}

export function pop(): void {
  YYPNavigationModule.pop();
}
