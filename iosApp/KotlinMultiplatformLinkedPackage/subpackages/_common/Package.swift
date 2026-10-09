// swift-tools-version: 5.9
import PackageDescription
let package = Package(
  name: "_common",
  platforms: [
    .iOS("15.5")
  ],
  products: [
    .library(
      name: "_common",
      type: .none,
      targets: ["_common"]
    )
  ],
  dependencies: [
    .package(
      url: "https://github.com/ZipArchive/ZipArchive.git",
      from: "2.6.0"
    ),
    .package(
      url: "https://github.com/nicklockwood/GZIP.git",
      from: "1.3.2"
    ),
    .package(
      url: "https://github.com/firebase/firebase-ios-sdk.git",
      exact: "12.18.0"
    )
  ],
  targets: [
    .target(
      name: "_common",
      dependencies: [
        .product(
          name: "ZipArchive",
          package: "ZipArchive"
        ),
        .product(
          name: "GZIP",
          package: "GZIP"
        ),
        .product(
          name: "FirebaseRemoteConfig",
          package: "firebase-ios-sdk"
        ),
        .product(
          name: "FirebaseCrashlytics",
          package: "firebase-ios-sdk"
        )
      ]
    )
  ]
)
