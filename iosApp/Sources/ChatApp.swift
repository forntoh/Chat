import SwiftUI
import ChatSample

@main
struct ChatApp: App {
    var body: some Scene { WindowGroup { ChatView().ignoresSafeArea(.keyboard) } }
}

struct ChatView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
