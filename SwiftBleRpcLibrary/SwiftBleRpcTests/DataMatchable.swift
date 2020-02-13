import Foundation
import Cuckoo
import CryptoSwift

/// Extension for *Data* to support matching two objects. Matches raw bytes.
extension Data: Matchable {
    func equal(to value: Data) -> ParameterMatcher<Data> {
        return ParameterMatcher { tested in
            tested.bytes == value.bytes
        }
    }
}
