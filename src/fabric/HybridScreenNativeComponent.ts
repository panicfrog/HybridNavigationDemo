import type { CodegenTypes as CT, ViewProps } from 'react-native';
import { codegenNativeComponent } from 'react-native';

type ScreenEvent = Readonly<{ routeID: string }>;

export interface NativeProps extends ViewProps {
  routeID: string;

  onWillAppear?: CT.DirectEventHandler<ScreenEvent> | undefined;
  onDidAppear?: CT.DirectEventHandler<ScreenEvent> | undefined;
  onWillDisappear?: CT.DirectEventHandler<ScreenEvent> | undefined;
  onDidDisappear?: CT.DirectEventHandler<ScreenEvent> | undefined;
  onNativeDismiss?: CT.DirectEventHandler<ScreenEvent> | undefined;
}

export default codegenNativeComponent<NativeProps>('HybridScreen');
