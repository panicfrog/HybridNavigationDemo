//
//  YYPNavigationModule.m
//  HybridNavigationDemo
//
//  Created by yeyongping on 2026/6/29.
//

#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>

@interface RCT_EXTERN_MODULE (YYPNavigationModule, RCTEventEmitter)

RCT_EXTERN_METHOD(registerScreen:(NSString *)routePath
                  module:(NSString *)module
              schemaJSON:(NSString *)schemaJSON)
RCT_EXTERN_METHOD(pushRoute:(NSString *)routePath
                  module:(NSString *)module
              paramsJSON:(NSString *)paramsJSON)
RCT_EXTERN_METHOD(pop)

@end
