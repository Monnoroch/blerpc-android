Pod::Spec.new do |s|
  s.name             = "SwiftBleRpcLibrary"
  s.version          = "0.0.1"
  s.summary          = "SwiftBleRpcLibrary"
  s.description      = "SwiftBleRpcLibrary"
  s.homepage         = "https://github.com/Monnoroch/blerpc-android"
  s.license          = 'MIT'
  s.author           = { "Monnoroch" => "monnoroch@gmail.com" }
  s.source           = { :git => "https://github.com/Monnoroch/blerpc-android.git", :branch => "swift-ios-support" }

  s.requires_arc          = true

  s.ios.deployment_target = '9.0'
  s.watchos.deployment_target = '6.0'

  s.source_files          = 'SwiftBleRpcLibrary/SwiftBleRpc/**/*.swift'
  s.dependency 'RxBluetoothKit'
  s.dependency 'SwiftGRPC'
  s.dependency 'CryptoSwift'

  s.swift_version = '5.0'
end
