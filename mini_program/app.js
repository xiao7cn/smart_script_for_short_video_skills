App({
  globalData: {
    statusBarHeight: 20,
    navBarHeight: 56,
  },
  onLaunch() {
    const sys = wx.getSystemInfoSync();
    this.globalData.statusBarHeight = sys.statusBarHeight || 20;
  },
});
