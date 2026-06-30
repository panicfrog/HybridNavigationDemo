import UIKit

class NativeViewControllerA: UIViewController {
  
  override func viewDidLoad() {
    super.viewDidLoad()
    title = "Native Screen A01"
    view.backgroundColor = UIColor(red: 0.1, green: 0.4, blue: 0.9, alpha: 1)
    
    let label = UILabel()
    label.text = "原生页面 A"
    label.textColor = .white
    label.font = .systemFont(ofSize: 18, weight: .medium)
    label.translatesAutoresizingMaskIntoConstraints = false
    
    let pushBButton = makeButton(title: "Push RN Screen B →")
    pushBButton.addTarget(self, action: #selector(pushScreenB), for: .touchUpInside)
    pushBButton.translatesAutoresizingMaskIntoConstraints = false
    
    view.addSubview(label)
    view.addSubview(pushBButton)
    
    NSLayoutConstraint.activate([
      label.centerXAnchor.constraint(equalTo: view.centerXAnchor),
      label.centerYAnchor.constraint(equalTo: view.centerYAnchor, constant: -40),
      pushBButton.centerXAnchor.constraint(equalTo: view.centerXAnchor),
      pushBButton.topAnchor.constraint(equalTo: label.bottomAnchor, constant: 24),
    ])
  }
  
  private func makeButton(title: String) -> UIButton {
    var config = UIButton.Configuration.plain()
    var attributed = AttributedString(title)
    attributed.font = .systemFont(ofSize: 18, weight: .semibold)
    config.attributedTitle = attributed
    config.baseForegroundColor = .white
    config.contentInsets = NSDirectionalEdgeInsets(top: 14, leading: 24, bottom: 14, trailing: 24)
    config.background.backgroundColor = UIColor(white: 1, alpha: 0.25)
    config.background.cornerRadius = 12
    return UIButton(configuration: config)
  }
  
  @objc private func pushScreenB() {
    // 演示带参数跳转：B 接收 title
    do {
      try YYPRouter.shared.pushRoute("B", module: "default", params: ["title": "Hello from A"]) {
        debugPrint("[NativeViewControllerA] B push 动画完成")
      }
    } catch {
      debugPrint("[NativeViewControllerA] pushRoute B failed: \(error)")
    }
  }
}
