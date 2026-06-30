import React from 'react';
import { StyleSheet } from 'react-native';
import HybridScreenNativeComponent from './fabric/HybridScreenNativeComponent';

type ActivityMode = 'attached' | 'detached';

interface HybridScreenProps {
  screenKey: string;
  activityMode: ActivityMode;
  children: React.ReactNode;
  onNativeDismiss?: (screenKey: string) => void;
  onWillAppear?: () => void;
  onDidAppear?: () => void;
  onWillDisappear?: () => void;
  onDidDisappear?: () => void;
}

export default function HybridScreen({
  screenKey,
  activityMode,
  children,
  onNativeDismiss,
  onWillAppear,
  onDidAppear,
  onWillDisappear,
  onDidDisappear,
}: HybridScreenProps) {
  return (
    <HybridScreenNativeComponent
      style={StyleSheet.absoluteFill}
      screenKey={screenKey}
      activityMode={activityMode}
      onNativeDismiss={() => onNativeDismiss?.(screenKey)}
      onWillAppear={onWillAppear}
      onDidAppear={onDidAppear}
      onWillDisappear={onWillDisappear}
      onDidDisappear={onDidDisappear}>
      {children}
    </HybridScreenNativeComponent>
  );
}
