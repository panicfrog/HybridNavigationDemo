import { z } from 'zod';
import { INDEX_PATH, type ScreenRegistry } from '@yyp/hybrid-navigation';
import ScreenB from './ScreenB';
import ScreenD from './ScreenD';
import ScreenRoot from './ScreenRoot';

/// routePath → RN 页面定义。新增 RN 页面：在这里加一行。
/// 原生页面（A、C）不在这里 —— 它们在 AppDelegate / MainActivity 用 YYPRouter 注册。
export const screens: ScreenRegistry = {
  // ↓↓↓ demo 默认走原生 Root(AppDelegate 注册 NativeViewControllerA 为 __index__)。
  // 若想验证 RN Root,取消下一行注释,并注释掉 AppDelegate 中 NativeViewControllerA 的 __index__ 注册。
  // [INDEX_PATH]: { screen: ScreenRoot },
  B: {
    screen: ScreenB,
    schema: z.object({ title: z.string().optional() }),
    title: 'RN Screen B',
  },
  D: {
    screen: ScreenD,
    title: 'RN Screen D',
  },
};

export { INDEX_PATH };
