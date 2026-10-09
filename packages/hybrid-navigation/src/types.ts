import type { ComponentType } from 'react';
import { ZodType } from 'zod';

/// Root 页 routePath 信号:取此值时,该 module 的注册项被原生识别为 Root,
/// 启动期主动 mount 后会触发原生换 window.rootViewController。
/// 必须与原生 YYPRouterConstants 的 YYPRouterRootIndexPath 一致。
export const INDEX_PATH = '__index__';

export interface ScreenDefinition<P = any> {
  screen: ComponentType<{ params?: P }>;
  /// registerScreen 时转 JSON Schema 传给原生；参数校验统一由原生 YYPRouter 完成（JS 不再校验）
  schema?: ZodType<P>;
  /// 导航栏标题（传给原生，Android header / iOS UINavigationBar 显示）
  title?: string;
}

/// routePath → 页面定义。RN 页面由业务方提供；原生页面在其原生入口注册。
export type ScreenRegistry = Record<string, ScreenDefinition>;
