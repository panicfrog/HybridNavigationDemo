import { NativeEventEmitter, NativeModules } from 'react-native';
import { zodToJsonSchema } from 'zod-to-json-schema';
import { type ScreenDefinition } from './screens';

const { YYPNavigationModule } = NativeModules;

export const hybridNavigationEmitter = new NativeEventEmitter(
  YYPNavigationModule,
);

/// 注册一个 RN 页面到 YYPRouter（platform = REACT_NATIVE）。
/// schema 通过 zod-to-json-schema 转 JSON Schema 字符串发给原生作为 metadata；
/// title 作为原生导航栏标题（Android header / iOS UINavigationBar）。
export function registerScreen(routePath: string, def: ScreenDefinition, module: string = 'default'): void {
  const schemaJSON = def.schema ? JSON.stringify(zodToJsonSchema(def.schema)) : '';
  YYPNavigationModule.registerScreen(routePath, module, schemaJSON, def.title ?? '');
}

/// 主动 push 一个页面，等原生转场动画完成后 resolve。
/// 参数校验统一由原生 YYPRouter 用 JSON Schema 完成（schema 来源见 screens.ts）。
export async function pushRoute(routePath: string, params?: any, module: string = 'default'): Promise<void> {
  const paramsJSON = params ? JSON.stringify(params) : '';
  await YYPNavigationModule.pushRoute(routePath, module, paramsJSON);
}

/// pop 当前栈顶，等转场动画完成后 resolve。
export async function pop(): Promise<void> {
  await YYPNavigationModule.pop();
}
