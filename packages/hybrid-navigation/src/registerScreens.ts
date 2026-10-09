import { zodToJsonSchema } from 'zod-to-json-schema';
import { NativeModules } from 'react-native';
import { type ScreenRegistry } from './types';

const { YYPNavigationModule } = NativeModules;

/// 批量注册 RN 页面到 YYPRouter（platform = REACT_NATIVE）。
/// schema 通过 zod-to-json-schema 转 JSON Schema 字符串发给原生作为 metadata；
/// title 作为原生导航栏标题（Android header / iOS UINavigationBar）。
export function registerScreens(screens: ScreenRegistry, module: string = 'default'): void {
  Object.entries(screens).forEach(([routePath, def]) => {
    const schemaJSON = def.schema ? JSON.stringify(zodToJsonSchema(def.schema)) : '';
    YYPNavigationModule.registerScreen(routePath, module, schemaJSON, def.title ?? '');
  });
}
