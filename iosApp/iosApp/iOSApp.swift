import FirebaseCore
import Shared
import SwiftUI

class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        FirebaseApp.configure()
        return true
    }
}

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate

    init() {
        AppInitializer.shared.onApplicationStart()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                // Shared playlist links: tvspace://import-playlist?name=…&url=…
                .onOpenURL { url in ImportLinks.shared.handle(link: url.absoluteString) }
        }
    }
}
