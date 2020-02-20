import XCTest
import Nimble
@testable import SwiftBleRpc

/// Proto parser tests (decoding/encoding proto messages).
class ProtoParserTests: XCTestCase {

    func testDecodeInt1Byte() throws {
        let decoded = try ProtoDecoder.decode(data: Data.init(hex: "7B"), from: 0, to: 1, type: .int32) as? Int32
        expect(decoded).to(equal(123))
    }

    func testDecodeInt2Bytes() throws {
        let decoded = try ProtoDecoder.decode(data: Data.init(hex: "7B45"), from: 0, to: 2, type: .int32) as? Int32
        expect(decoded).to(equal(17787))
    }

    func testDecodeInt2BytesFromLongData() throws {
        let decoded = try ProtoDecoder.decode(data: Data.init(hex: "7B45D4E6"), from: 0, to: 2, type: .int32) as? Int32
        expect(decoded).to(equal(17787))
    }

    func testDecodeInt4Bytes() throws {
        let decoded = try ProtoDecoder.decode(data: Data.init(hex: "7B45D4E6"), from: 0, to: 4, type: .int32) as? Int32
        expect(decoded).to(equal(-422296197))
    }

    func testDecodeBoolFalse() throws {
        let decoded = try ProtoDecoder.decode(data: Data.init(hex: "0"), from: 0, to: 1, type: .bool) as? Bool
        expect(decoded).to(equal(false))
    }

    func testDecodeBoolTrue() throws {
        let decoded = try ProtoDecoder.decode(data: Data.init(hex: "1"), from: 0, to: 1, type: .bool) as? Bool
        expect(decoded).to(equal(true))
    }

    func testDecodePartBytes() throws {
        let data = Data.init(hex: "3617254")
        let decoded = try ProtoDecoder.decode(data: data, from: 0, to: 2, type: .byte) as? Data
        expect(decoded).to(equal(Data.init(hex: "3617")))
    }

    func testDecodeFullBytes() throws {
        let data = Data.init(hex: "36172540")
        let decoded = try ProtoDecoder.decode(data: data, from: 0, to: 4, type: .byte) as? Data
        expect(decoded).to(equal(Data.init(hex: "36172540")))
    }

    func testDecodeIntWrongSize() {
        do {
            _ = try ProtoDecoder.decode(data: Data.init(hex: "7B"), from: 0, to: 4, type: .int32) as? Int32
            failTest()
        } catch {
            expect(String(reflecting: error)).to(equal(String(reflecting: ProtoParserErrors.wrongData)))
        }
    }

    func testDecodeWrongType() {
        do {
            _ = try ProtoDecoder.decode(data: Data.init(hex: "7B"), from: 0, to: 4, type: .unknown) as? Int32
            failTest()
        } catch {
            expect(String(reflecting: error)).to(equal(String(reflecting: ProtoParserErrors.notSupportedType)))
        }
    }

    func testDecodeBytesWrongSize() throws {
        do {
            _ = try ProtoDecoder.decode(data: Data.init(hex: "36172540"), from: 0, to: 10, type: .byte) as? Data
            failTest()
        } catch {
            expect(String(reflecting: error)).to(equal(String(reflecting: ProtoParserErrors.wrongData)))
        }
    }

    func testEncodeInt1Byte() throws {
        let encoded = try ProtoEncoder.encode(object: 123, from: 0, to: 1, type: .int32)
        expect(encoded).to(equal(Data.init(hex: "7B")))
    }

    func testEncodeInt2Byte() throws {
        let encoded = try ProtoEncoder.encode(object: 35278, from: 0, to: 2, type: .int32)
        expect(encoded).to(equal(Data.init(bytes: [206, 137])))
    }

    func testEncodeIntMoreThanFourBytesFails() throws {
        do {
            _ = try ProtoEncoder.encode(object: 35278, from: 0, to: 5, type: .int32)
            failTest()
        } catch {
            expect(String(reflecting: error)).to(equal(String(reflecting: ProtoParserErrors.wrongData)))
        }
    }

    func testEncodeBoolTrue() throws {
        let encoded = try ProtoEncoder.encode(object: true, from: 0, to: 1, type: .bool)
        expect(encoded).to(equal(Data.init(bytes: [1])))
    }

    func testEncodeBoolFalse() throws {
        let encoded = try ProtoEncoder.encode(object: false, from: 0, to: 1, type: .bool)
        expect(encoded).to(equal(Data.init(bytes: [0])))
    }

    func testEncodeBoolMoreThanOneBytesFails() throws {
        do {
            _ = try ProtoEncoder.encode(object: true, from: 0, to: 2, type: .bool)
            failTest()
        } catch {
            expect(String(reflecting: error)).to(equal(String(reflecting: ProtoParserErrors.wrongData)))
        }
    }

    func testEncodeEmptyBytes() throws {
        let encoded = try ProtoEncoder.encode(object: Data(), from: 0, to: 0, type: .byte)
        expect(encoded).to(equal(Data()))
    }

    func testEncodePartBytes() throws {
        let data = Data.init(hex: "36172540")
        let encoded = try ProtoEncoder.encode(object: data, from: 0, to: 1, type: .byte)
        print(encoded.bytes)
        expect(encoded).to(equal(Data.init(hex: "36")))
    }

    func testEncodeFullBytes() throws {
        let data = Data.init(hex: "36172540")
        let encoded = try ProtoEncoder.encode(object: data, from: 0, to: 4, type: .byte)
        expect(encoded).to(equal(data))
    }

    func testEncodeFromNotZeroBytes() throws {
        let data = Data.init(hex: "36172540")
        let encoded = try ProtoEncoder.encode(object: data, from: 2, to: 4, type: .byte)
        expect(encoded).to(equal(Data.init(hex: "2540")))
    }

    func testEncodeBytesWrongSize() throws {
        do {
            _ = try ProtoEncoder.encode(object: Data.init(hex: "36172540"), from: 0, to: 15, type: .byte)
            failTest()
        } catch {
            expect(String(reflecting: error)).to(equal(String(reflecting: ProtoParserErrors.wrongData)))
        }
    }

    func testEncodeWrongType() {
        do {
            _ = try ProtoEncoder.encode(object: 123, from: 0, to: 4, type: .unknown)
            failTest()
        } catch {
            expect(String(reflecting: error)).to(equal(String(reflecting: ProtoParserErrors.notSupportedType)))
        }
    }

    func failTest() {
        expect(false).to(equal(true))
    }
}
