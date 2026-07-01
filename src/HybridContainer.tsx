import React from 'react';
import { StyleSheet } from 'react-native';
import HybridContainerNativeComponent from './fabric/HybridContainerNativeComponent';

interface HybridContainerProps {
  children: React.ReactNode;
}

// 透明容器，承担 Fabric mount 观察职责。
// 子组件（HybridScreen）的 mount/unmount 通过 NSNotification 通知出去；
// 路由执行由 YYPRouter 完成。
export default function HybridContainer({ children }: HybridContainerProps) {
  return (
    <HybridContainerNativeComponent style={StyleSheet.absoluteFill}>
      {children}
    </HybridContainerNativeComponent>
  );
}
