import UIKit
import JSONSchema
import JSONSchemaBuilder

@Schemable
struct NativeViewControllerCParams {
  let message: String
}

class NativeViewControllerC: UIViewController {
  
  private let message: String
  
  init(message: String) {
    self.message = message
    super.init(nibName: nil, bundle: nil)
  }
  
  required init?(coder: NSCoder) {
    fatalError("init(coder:) has not been implemented")
  }
  
  override func viewDidLoad() {
    super.viewDidLoad()
    title = "Native Screen C"
    view.backgroundColor = UIColor(red: 0.1, green: 0.6, blue: 0.3, alpha: 1)
    
    let label = UILabel()
    label.text = message
    label.textColor = .white
    label.font = .systemFont(ofSize: 18, weight: .medium)
    label.translatesAutoresizingMaskIntoConstraints = false
    
    var buttonConfig = UIButton.Configuration.plain()
    var attributed = AttributedString("Push RN Screen D →")
    attributed.font = .systemFont(ofSize: 18, weight: .semibold)
    buttonConfig.attributedTitle = attributed
    buttonConfig.baseForegroundColor = .white
    buttonConfig.contentInsets = NSDirectionalEdgeInsets(top: 14, leading: 24, bottom: 14, trailing: 24)
    buttonConfig.background.backgroundColor = UIColor(white: 1, alpha: 0.25)
    buttonConfig.background.cornerRadius = 12
    let button = UIButton(configuration: buttonConfig)
    button.addTarget(self, action: #selector(pushScreenD), for: .touchUpInside)
    button.translatesAutoresizingMaskIntoConstraints = false
    
    view.addSubview(label)
    view.addSubview(button)
    
    NSLayoutConstraint.activate([
      label.centerXAnchor.constraint(equalTo: view.centerXAnchor),
      label.centerYAnchor.constraint(equalTo: view.centerYAnchor, constant: -40),
      button.centerXAnchor.constraint(equalTo: view.centerXAnchor),
      button.topAnchor.constraint(equalTo: label.bottomAnchor, constant: 24),
    ])
  }
  
  @objc private func pushScreenD() {
    do {
      try YYPRouter.shared.pushRoute("D", module: "default", params: nil)
    } catch {
      debugPrint("[NativeViewControllerC] pushRoute D failed: \(error)")
    }
  }
}

extension NativeViewControllerC: YYPRouterScreenProvider {
  typealias Params = NativeViewControllerCParams
  
  static func router_createViewController(withParams params: Params) -> UIViewController {
    return NativeViewControllerC(message: params.message)
  }
}
