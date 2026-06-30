import React from 'react';
import { StyleSheet } from 'react-native';
import HybridContainerNativeComponent from './fabric/HybridContainerNativeComponent';

interface HybridContainerProps {
  routePath: string;
  children: React.ReactNode;
}

// 透明容器，承担 Fabric mount 观察职责。
// 通过 routePath 标识自己，把所有挂载状态以 NSNotification 通知出去；
// 路由执行由 YYPRouter 完成。
export default function HybridContainer({ routePath, children }: HybridContainerProps) {
  return (
    <HybridContainerNativeComponent style={StyleSheet.absoluteFill} routePath={routePath}>
      {children}
    </HybridContainerNativeComponent>
  );
}
