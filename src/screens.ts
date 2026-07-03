import type { ComponentType } from 'react';
import { z, ZodType } from 'zod';
import ScreenB from './ScreenB';
import ScreenD from './ScreenD';
import ScreenRoot from './ScreenRoot';

/// Root 页 routePath 信号:取此值时,该 module 的注册项被原生识别为 Root,
/// 启动期主动 mount 后会触发原生换 window.rootViewController。
/// 必须与 ios/.../YYPRouterConstants.m 的 YYPRouterRootIndexPath 一致。
export const INDEX_PATH = '__index__';

export interface ScreenDefinition<P = any> {
  screen: ComponentType<{ params?: P }>;
  /// registerScreen 时转 JSON Schema 传给原生；参数校验统一由原生 YYPRouter 完成（JS 不再校验）
  schema?: ZodType<P>;
}

/// routePath → RN 页面定义。新增 RN 页面：在这里加一行。
/// 原生页面（A、C）不在这里 —— 它们在 AppDelegate 用 YYPRouter.registerScreen 注册。
export const screens: Record<string, ScreenDefinition> = {
  // ↓↓↓ demo 默认走原生 Root(AppDelegate 注册 NativeViewControllerA 为 __index__)。
  // 若想验证 RN Root,取消下一行注释,并注释掉 AppDelegate 中 NativeViewControllerA 的 __index__ 注册。
  // [INDEX_PATH]: { screen: ScreenRoot },
  B: {
    screen: ScreenB,
    schema: z.object({ title: z.string().optional() }),
  },
  D: { screen: ScreenD },
};
