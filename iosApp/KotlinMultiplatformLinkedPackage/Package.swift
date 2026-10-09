// swift-tools-version: 5.9
import PackageDescription
let package = Package(
  name: "KotlinMultiplatformLinkedPackage",
  platforms: [
    .iOS("15.5")
  ],
  products: [
    .library(
      name: "KotlinMultiplatformLinkedPackage",
      type: .none,
      targets: ["KotlinMultiplatformLinkedPackage"]
    )
  ],
  dependencies: [
    .package(path: "subpackages/_monetization"),
    .package(path: "subpackages/_common"),
    .package(path: "subpackages/_mmp_firebase")
  ],
  targets: [
    .target(
      name: "KotlinMultiplatformLinkedPackage",
      dependencies: [
        .product(name: "_monetization", package: "_monetization"),
        .product(name: "_common", package: "_common"),
        .product(name: "_mmp_firebase", package: "_mmp_firebase")
      ]
    )
  ]
)
