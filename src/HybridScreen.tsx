import React from 'react';
import { StyleSheet } from 'react-native';
import HybridScreenNativeComponent from './fabric/HybridScreenNativeComponent';

interface HybridScreenProps {
  routeID: string;
  children: React.ReactNode;
  onNativeDismiss?: (routeID: string) => void;
  onWillAppear?: (routeID: string) => void;
  onDidAppear?: (routeID: string) => void;
  onWillDisappear?: (routeID: string) => void;
  onDidDisappear?: (routeID: string) => void;
}

export default function HybridScreen({
  routeID,
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
      routeID={routeID}
      onNativeDismiss={(e) => onNativeDismiss?.(e.nativeEvent.routeID)}
      onWillAppear={(e) => onWillAppear?.(e.nativeEvent.routeID)}
      onDidAppear={(e) => onDidAppear?.(e.nativeEvent.routeID)}
      onWillDisappear={(e) => onWillDisappear?.(e.nativeEvent.routeID)}
      onDidDisappear={(e) => onDidDisappear?.(e.nativeEvent.routeID)}>
      {children}
    </HybridScreenNativeComponent>
  );
}
