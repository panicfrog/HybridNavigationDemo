import type { ComponentType } from 'react';
import { z, ZodType } from 'zod';
import ScreenB from './ScreenB';
import ScreenD from './ScreenD';

export interface ScreenDefinition<P = any> {
  screen: ComponentType<{ params?: P }>;
  /// 可选 zod schema。pushRoute 时用 safeParse 校验 params；registerScreen 时转 JSON Schema 传给原生
  schema?: ZodType<P>;
}

/// routePath → RN 页面定义。新增 RN 页面：在这里加一行。
/// 原生页面（A、C）不在这里 —— 它们在 AppDelegate 用 YYPRouter.registerScreen 注册。
export const screens: Record<string, ScreenDefinition> = {
  B: {
    screen: ScreenB,
    schema: z.object({ title: z.string().optional() }),
  },
  D: { screen: ScreenD },
};
