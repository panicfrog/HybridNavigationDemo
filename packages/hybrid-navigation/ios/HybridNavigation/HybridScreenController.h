#pragma once

#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

@class HybridScreenComponentView;

@interface HybridScreenController : UIViewController

- (instancetype)initWithComponentView:(HybridScreenComponentView *)componentView;

@end

NS_ASSUME_NONNULL_END
