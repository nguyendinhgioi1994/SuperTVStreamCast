// swift-tools-version: 5.9
import PackageDescription
let package = Package(
  name: "_monetization",
  platforms: [
    .iOS("15.5")
  ],
  products: [
    .library(
      name: "_monetization",
      type: .none,
      targets: ["_monetization"]
    )
  ],
  dependencies: [
    .package(
      url: "https://github.com/googleads/swift-package-manager-google-mobile-ads.git",
      exact: "13.8.0"
    ),
    .package(
      url: "https://github.com/googleads/swift-package-manager-google-user-messaging-platform.git",
      from: "3.1.0"
    ),
    .package(
      url: "https://github.com/qonversion/qonversion-ios-sdk.git",
      from: "6.14.0"
    )
  ],
  targets: [
    .target(
      name: "_monetization",
      dependencies: [
        .product(
          name: "GoogleMobileAds",
          package: "swift-package-manager-google-mobile-ads"
        ),
        .product(
          name: "GoogleUserMessagingPlatform",
          package: "swift-package-manager-google-user-messaging-platform"
        ),
        .product(
          name: "Qonversion",
          package: "qonversion-ios-sdk"
        )
      ]
    )
  ]
)
