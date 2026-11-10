import { defineConfig } from "@apps-in-toss/web-framework/config";

export default defineConfig({
  appName: "insightdeal",
  webBundleDir: "out",
  permissions: [
    {
      name: "clipboard",
      access: "write",
    },
  ],
  navigationBar: {
    withBackButton: true,
    withHomeButton: false,
    withTitle: true,
    transparentBackground: false,
    theme: "light",
  },
  webView: {
    pullToRefreshEnabled: false,
    allowsBackForwardNavigationGestures: true,
    bounces: true,
  },
});
