import SwiftUI
import ChatSample

@main
struct ChatApp: App {
    var body: some Scene { WindowGroup { ChatView().ignoresSafeArea() } }
}

struct ChatView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        return MainViewControllerKt.MainViewController()
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
