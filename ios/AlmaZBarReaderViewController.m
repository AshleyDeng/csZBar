//
//  AlmaZBarReaderViewController.m
//  BarCodeMix
//
//  Created by eCompliance on 23/01/15.

#import "AlmaZBarReaderViewController.h"
#import "CsZbar.h"

@interface AlmaZBarReaderViewController ()

@end

@implementation AlmaZBarReaderViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    // Do any additional setup after loading the view.
}

- (BOOL)prefersStatusBarHidden {
    return YES;
}

- (void)didReceiveMemoryWarning {
    [super didReceiveMemoryWarning];
    // Dispose of any resources that can be recreated.
}

- (BOOL)shouldAutorotate{
    return NO;
}

- (void) didRotateFromInterfaceOrientation:(UIInterfaceOrientation) fromInterfaceOrientation {
    // AlmaZBarReaderViewController.scanner.scanner.cameraOverlayView = poli
    // NSDictionary *params = (NSDictionary*) [command argumentAtIndex:0];
    BOOL drawSight = true;//[params objectForKey:@"drawSight"] ? [[params objectForKey:@"drawSight"] boolValue] : true;

    if (drawSight) {
        CGRect screenRect = [[UIScreen mainScreen] bounds];
        CGFloat screenWidth = screenRect.size.width;
        CGFloat screenHeight = screenRect.size.height;
        CGFloat dim = screenWidth < screenHeight ? screenWidth / 1.1 : screenHeight / 1.1;
        UIView *polygonView = [[UIView alloc] initWithFrame: CGRectMake  ( (screenWidth/2) - (dim/2), (screenHeight/2) - (dim/2), dim, dim)];

        UIView *lineView = [[UIView alloc] initWithFrame:CGRectMake(0,dim / 2, dim, 1)];
        lineView.backgroundColor = [UIColor redColor];
        [polygonView addSubview:lineView];
        self.cameraOverlayView = polygonView;
    }
}

@end
