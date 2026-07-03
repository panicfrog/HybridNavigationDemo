import UIKit
import JSONSchema
import JSONSchemaBuilder

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

    let pushBHiButton = makeButton(title: "Push B (title=Hi) →")
    pushBHiButton.addTarget(self, action: #selector(pushScreenBWithHi), for: .touchUpInside)
    pushBHiButton.translatesAutoresizingMaskIntoConstraints = false

    let pushBHelloButton = makeButton(title: "Push B (title=Hello) →")
    pushBHelloButton.addTarget(self, action: #selector(pushScreenBWithHello), for: .touchUpInside)
    pushBHelloButton.translatesAutoresizingMaskIntoConstraints = false

    view.addSubview(label)
    view.addSubview(pushBHiButton)
    view.addSubview(pushBHelloButton)

    NSLayoutConstraint.activate([
      label.centerXAnchor.constraint(equalTo: view.centerXAnchor),
      label.centerYAnchor.constraint(equalTo: view.centerYAnchor, constant: -60),
      pushBHiButton.centerXAnchor.constraint(equalTo: view.centerXAnchor),
      pushBHiButton.topAnchor.constraint(equalTo: label.bottomAnchor, constant: 20),
      pushBHelloButton.centerXAnchor.constraint(equalTo: view.centerXAnchor),
      pushBHelloButton.topAnchor.constraint(equalTo: pushBHiButton.bottomAnchor, constant: 12),
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

  // 同 routePath push 两次测试：两个按钮 push 同一个 "B"，但 params 不同
  @objc private func pushScreenBWithHi() {
    pushScreenB(title: "Hi")
  }

  @objc private func pushScreenBWithHello() {
    pushScreenB(title: "Hello")
  }

  private func pushScreenB(title: String) {
    do {
      try YYPRouter.shared.pushRoute("B", module: "default", params: ["title": title]) {
        debugPrint("[NativeViewControllerA] B (title=\(title)) push 动画完成")
      }
    } catch {
      debugPrint("[NativeViewControllerA] pushRoute B failed: \(error)")
    }
  }
}

extension NativeViewControllerA: YYPRouterScreenProvider {
  @Schemable struct Params {}
  static func router_createViewController(withParams params: Params) -> UIViewController {
    return NativeViewControllerA()
  }
}
