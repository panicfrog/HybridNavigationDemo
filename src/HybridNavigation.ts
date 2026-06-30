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

/// 主动 push 一个页面，等原生转场动画完成后 resolve。
/// params 先用 zod schema 校验（不通过直接 throw，不发到原生）。
export async function pushRoute(routePath: string, params?: any, module: string = 'default'): Promise<void> {
  const def = screens[routePath];
  if (def?.schema && params !== undefined) {
    const result = def.schema.safeParse(params);
    if (!result.success) {
      throw new Error(`Invalid params for ${routePath}: ${JSON.stringify(result.error.issues)}`);
    }
  }
  const paramsJSON = params ? JSON.stringify(params) : '';
  await YYPNavigationModule.pushRoute(routePath, module, paramsJSON);
}

/// pop 当前栈顶，等转场动画完成后 resolve。
export async function pop(): Promise<void> {
  await YYPNavigationModule.pop();
}
