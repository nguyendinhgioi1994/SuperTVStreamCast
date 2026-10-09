// swift-tools-version: 5.9
import PackageDescription
let package = Package(
  name: "_mmp_firebase",
  platforms: [
    .iOS("15.5")
  ],
  products: [
    .library(
      name: "_mmp_firebase",
      type: .none,
      targets: ["_mmp_firebase"]
    )
  ],
  dependencies: [
    .package(
      url: "https://github.com/firebase/firebase-ios-sdk.git",
      from: "12.18.0"
    )
  ],
  targets: [
    .target(
      name: "_mmp_firebase",
      dependencies: [
        .product(
          name: "FirebaseAnalytics",
          package: "firebase-ios-sdk"
        )
      ]
    )
  ]
)
