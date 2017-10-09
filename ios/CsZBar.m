#import "CsZBar.h"
#import <AVFoundation/AVFoundation.h>
#import "AlmaZBarReaderViewController.h"

#pragma mark - State

@interface CsZBar ()
@property bool scanInProgress;
@property NSString *scanCallbackId;
@property AlmaZBarReaderViewController *scanReader;

@end

#pragma mark - Synthesize

@implementation CsZBar

@synthesize scanInProgress;
@synthesize scanCallbackId;
@synthesize scanReader;

#pragma mark - Cordova Plugin

- (void)pluginInitialize {
    self.scanInProgress = NO;
}

- (void)willRotateToInterfaceOrientation:(UIInterfaceOrientation)toInterfaceOrientation duration:(NSTimeInterval)duration {
    return;
}

- (BOOL)shouldAutorotateToInterfaceOrientation:(UIInterfaceOrientation)interfaceOrientation {
    return NO;
}

#pragma mark - Plugin API

- (void)scan: (CDVInvokedUrlCommand*)command {
    if (self.scanInProgress) {
        self.scanCallbackId = [command callbackId];
    } else {
        self.scanInProgress = YES;
        self.scanCallbackId = [command callbackId];
        
        self.scanReader = [AlmaZBarReaderViewController new];
        self.scanReader.readerDelegate = self;
        self.scanReader.supportedOrientationsMask = ZBarOrientationMask(UIInterfaceOrientationPortrait);
        
        NSDictionary *params = (NSDictionary*) [command argumentAtIndex:0];
        [self handleUserParams: params];
        [self hideToolBar];
//        [self drawSight: params];
        [self addButtons];
        [self configView];
        
        [self.viewController.view addSubview:self.scanReader.view];
    }
}

- (void)cancel: (CDVInvokedUrlCommand*)command {
    self.scanInProgress = NO;
    [self sendScanResult: [CDVPluginResult
                           resultWithStatus: CDVCommandStatus_ERROR
                           messageAsString: @"cancelled"]];
    [self.scanReader.view removeFromSuperview];
}

- (void)handleUserParams: (NSDictionary*)params {
    // Get user parameters
    NSString *camera = [params objectForKey:@"camera"];
    if ([camera isEqualToString:@"front"]) {
        // We do not set any specific device for the default "back" setting,
        // as not all devices will have a rear-facing camera.
        self.scanReader.cameraDevice = UIImagePickerControllerCameraDeviceFront;
    }
    self.scanReader.cameraFlashMode = UIImagePickerControllerCameraFlashModeOn;
    
    NSString *flash = [params objectForKey:@"flash"];
    
    if ([flash isEqualToString:@"on"]) {
        self.scanReader.cameraFlashMode = UIImagePickerControllerCameraFlashModeOn;
    } else if ([flash isEqualToString:@"off"]) {
        self.scanReader.cameraFlashMode = UIImagePickerControllerCameraFlashModeOff;
    } else if ([flash isEqualToString:@"auto"]) {
        self.scanReader.cameraFlashMode = UIImagePickerControllerCameraFlashModeAuto;
    }
}

- (void)hideToolBar {
    // Hack to hide the bottom bar's Info button... originally based on http://stackoverflow.com/a/16353530
    NSInteger infoButtonIndex;

    if ([[[UIDevice currentDevice] systemVersion] compare:@"10.0" options:NSNumericSearch] != NSOrderedAscending) {
        infoButtonIndex = 0;
    } else {
        infoButtonIndex = 3;
    }
    
    UIView *bottomBar = [self.scanReader.view.subviews objectAtIndex:2];
    [bottomBar setHidden:YES];
}

- (void)addButtons {
    CGRect screenRect = [[UIScreen mainScreen] bounds];
    CGFloat screenWidth = screenRect.size.width;
    CGFloat screenHeight = screenRect.size.height;
    
    UIToolbar *toolbarView = [[UIToolbar alloc] init];
    [toolbarView setBackgroundImage:[UIImage new]
                      forToolbarPosition:UIBarPositionAny
                              barMetrics:UIBarMetricsDefault];
    [toolbarView setShadowImage:[UIImage new]
              forToolbarPosition:UIBarPositionAny];
    
    //The bar length it depends on the orientation
    toolbarView.frame = CGRectMake(0, 10, (screenWidth > screenHeight ? screenWidth : screenHeight), 44.0);

    UIBarButtonItem *buttonFlash = [[UIBarButtonItem alloc] initWithTitle:@"Flash" style:UIBarButtonItemStyleDone target:self action:@selector(toggleflash)];
    UIBarButtonItem *buttonCancel = [[UIBarButtonItem alloc] initWithTitle:@"Cancel" style:UIBarButtonItemStyleDone target:self action:@selector(cancel:)];

    
    NSArray *buttons = [NSArray arrayWithObjects: buttonFlash, buttonCancel, nil];
    [toolbarView setItems:buttons animated:YES];
    [self.scanReader.view addSubview:toolbarView];
}

- (void)drawSight: (NSDictionary*)params {
//    CGRect screenRect = [[UIScreen mainScreen] bounds];
    CGRect screenRect = self.scanReader.view.bounds;
    CGFloat screenWidth = screenRect.size.width;
    CGFloat screenHeight = screenRect.size.height / 2.0;
    
    BOOL drawSight = [params objectForKey:@"drawSight"] ? [[params objectForKey:@"drawSight"] boolValue] : true;
    if (drawSight) {
        UIView *polygonView = [[UIView alloc] initWithFrame: CGRectMake  (0, 0, screenWidth, screenHeight)];
        
        UIView *lineViewHorizontal = [[UIView alloc] initWithFrame:CGRectMake(10, screenHeight / 2, screenWidth - 10, 1)];
        lineViewHorizontal.backgroundColor = [UIColor redColor];
        [polygonView addSubview:lineViewHorizontal];
        
        UIView *lineViewVertical = [[UIView alloc] initWithFrame:CGRectMake(screenWidth / 2, 30, 1, screenHeight - 40)];
        lineViewVertical.backgroundColor = [UIColor redColor];
        [polygonView addSubview:lineViewVertical];
        
        self.scanReader.cameraOverlayView = polygonView;
    }
}

- (void)configView {
    CGRect frame = self.scanReader.view.frame;
    frame.size.width = [[UIScreen mainScreen] bounds].size.width;
    frame.size.height = [[UIScreen mainScreen] bounds].size.height / 2.0;
    
    self.scanReader.view.frame = frame;
    self.scanReader.view.subviews.firstObject.frame = frame;
}

- (void)toggleflash {
//    NSLog(@"Toggle");
    AVCaptureDevice *device = [AVCaptureDevice defaultDeviceWithMediaType:AVMediaTypeVideo];
    
    [device lockForConfiguration:nil];
    if (device.torchAvailable == 1) {
        if (device.torchMode == 0) {
            [device setTorchMode:AVCaptureTorchModeOn];
            [device setFlashMode:AVCaptureFlashModeOn];
        } else {
            [device setTorchMode:AVCaptureTorchModeOff];
            [device setFlashMode:AVCaptureFlashModeOff];
        }
    }
    
    [device unlockForConfiguration];
}

#pragma mark - Helpers

- (void)sendScanResult: (CDVPluginResult*)result {
    [self.commandDelegate sendPluginResult: result callbackId: self.scanCallbackId];
}

#pragma mark - ZBarReaderDelegate

- (void) imagePickerController:(UIImagePickerController *)picker didFinishPickingImage:(UIImage *)image editingInfo:(NSDictionary *)editingInfo {
    return;
}

- (void)imagePickerController:(UIImagePickerController*)picker didFinishPickingMediaWithInfo:(NSDictionary*)info {
    static unsigned int count = 0;
    
    if ([self.scanReader isBeingDismissed]) {
        return;
    }
    
    id<NSFastEnumeration> results = [info objectForKey: ZBarReaderControllerResults];
    
    ZBarSymbol *symbol = nil;
    for (symbol in results) break; // get the first result
    NSLog(@"%@", symbol.data);
    
    count++;
    NSLog(@"%d", count);

    [self sendScanResult: [CDVPluginResult
                           resultWithStatus: CDVCommandStatus_OK
                           messageAsString: symbol.data]];
}

- (void) imagePickerControllerDidCancel:(UIImagePickerController*)picker {    
    self.scanInProgress = NO;
    [self sendScanResult: [CDVPluginResult
                           resultWithStatus: CDVCommandStatus_ERROR
                           messageAsString: @"cancelled"]];
    [self.scanReader.view removeFromSuperview];
}

- (void) readerControllerDidFailToRead:(ZBarReaderController*)reader withRetry:(BOOL)retry {
    self.scanInProgress = NO;
    [self sendScanResult: [CDVPluginResult
                           resultWithStatus: CDVCommandStatus_ERROR
                           messageAsString: @"Failed"]];
    [self.scanReader.view removeFromSuperview];}

@end
