//
//  YYPNavigationModule.m
//  HybridNavigationDemo
//
//  Created by yeyongping on 2026/6/29.
//

#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>

@interface RCT_EXTERN_MODULE (YYPNavigationModule, RCTEventEmitter)

RCT_EXTERN__BLOCKING_SYNCHRONOUS_METHOD(registerScreen:(NSString *)routePath
                  module:(NSString *)module
                  schemaJSON:(NSString *)schemaJSON
                  title:(NSString *)title)

RCT_EXTERN_METHOD(pushRoute:(NSString *)routePath
                  module:(NSString *)module
                  paramsJSON:(NSString *)paramsJSON
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(pop:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

@end
