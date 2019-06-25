import Foundation
import Cuckoo
import CryptoSwift

extension Data: Matchable {
    
    func equal(to value: Data) -> ParameterMatcher<Data> {
        return ParameterMatcher { tested in
            tested.bytes == value.bytes
        }
    }
    
}
