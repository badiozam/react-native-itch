#import <UIKit/UIKit.h>
#import <React/RCTComponent.h>
#import <React/RCTEventDispatcher.h>
#import "ScratchViewDelegate.h"

@interface ScratchView : UIImageView
{
  NSString *imageUrl;
  NSString *resourceName;
  NSString *resizeMode;
  CGRect imageRect;
  CGRect criticalCircleRect;
  UIColor *placeholderColor;
  UIColor *criticalColor;
  float threshold;
  float brushSize;
  UIImage *backgroundColorImage;
  UIImage *image;
  UIBezierPath *path;
  float minDimension;
  float maxDimension;
  float gridSize;
  NSMutableArray *grid;
  bool cleared;
  int clearPointsCounter;
  float scratchProgress;
  bool imageTakenFromView;

  float totalCriticalPoints;
  int clearedCriticalPoints;
  float criticalProgress;
  float criticalRadius;
  float criticalRadiusSq;
  float criticalCenterX;
  float criticalCenterY;

  id<ScratchViewDelegate> _delegate;
}

@property(nonatomic, assign) id<ScratchViewDelegate> _delegate;

@property(nonatomic, copy) RCTBubblingEventBlock onImageLoadFinished;
@property(nonatomic, copy) RCTBubblingEventBlock onTouchStateChanged;
@property(nonatomic, copy) RCTBubblingEventBlock onScratchProgressChanged;
@property(nonatomic, copy) RCTBubblingEventBlock onCriticalProgressChanged;
@property(nonatomic, copy) RCTBubblingEventBlock onScratchDone;

- (id)init;
- (void)reset;

@end
