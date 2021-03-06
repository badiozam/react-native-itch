@class ScratchView;

@protocol ScratchViewDelegate
@required
-(void) onImageLoadFinished:(ScratchView *)sender successState:(BOOL)state;
-(void) onTouchStateChanged:(ScratchView *)sender touchState:(BOOL)state;
-(void) onScratchProgressChanged:(ScratchView *)sender didChangeProgress:(CGFloat)scratchProgress;
-(void) onCriticalProgressChanged:(ScratchView *)sender didChangeProgress:(CGFloat)criticalProgress;
-(void) onScratchDone:(ScratchView *)sender isScratchDone:(BOOL)isDone;
@end

