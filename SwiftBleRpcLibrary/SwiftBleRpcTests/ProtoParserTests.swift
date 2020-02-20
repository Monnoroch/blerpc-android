import XCTest
import Nimble
@testable import SwiftBleRpc

/// Proto parser tests (decoding/encoding proto messages).
class ProtoParserTests: XCTestCase {

    func testDecodeInt1Byte() throws {
        let decoded = try ProtoDecoder.decode(data: Data.init(hex: "AA7BAA"), from: 1, to: 2, type: .int32) as? Int32
        expect(decoded).to(equal(123))
    }

    func testDecodeInt2Bytes() throws {
        let decoded = try ProtoDecoder.decode(data: Data.init(hex: "AA7B45AA"), from: 1, to: 3, type: .int32) as? Int32
        expect(decoded).to(equal(17787))
    }

    func testDecodeInt4Bytes() throws {
        let decoded = try ProtoDecoder.decode(data: Data.init(hex: "AA7B45D4E6AA"), from: 1, to: 5, type: .int32) as? Int32
        expect(decoded).to(equal(-422296197))
    }

    func testDecodeIntMoreThan4BytesFails() throws {
        expect { try ProtoDecoder.decode(data: Data.init(hex: "AA7B45D4E6AA"), from: 0, to: 5, type: .int32) as? Int32 }.to(throwError(ProtoParserErrors.wrongData))
    }

    func testDecodeBoolFalse() throws {
        let decoded = try ProtoDecoder.decode(data: Data.init(hex: "AA00AA"), from: 1, to: 2, type: .bool) as? Bool
        expect(decoded).to(equal(false))
    }

    func testDecodeBoolTrue() throws {
        let decoded = try ProtoDecoder.decode(data: Data.init(hex: "AA01AA"), from: 1, to: 2, type: .bool) as? Bool
        expect(decoded).to(equal(true))
    }

    func testDecodePartBytes() throws {
        let data = Data.init(hex: "AA3617254AA")
        let decoded = try ProtoDecoder.decode(data: data, from: 1, to: 3, type: .byte) as? Data
        expect(decoded).to(equal(Data.init(hex: "3617")))
    }

    func testDecodeFullBytes() throws {
        let data = Data.init(hex: "AA36172540AA")
        let decoded = try ProtoDecoder.decode(data: data, from: 1, to: 5, type: .byte) as? Data
        expect(decoded).to(equal(Data.init(hex: "36172540")))
    }

    func testDecodeIntWrongSize() {
        expect { try ProtoDecoder.decode(data: Data.init(hex: "7B"), from: 0, to: 4, type: .int32) as? Int32 }.to(throwError(ProtoParserErrors.wrongData))
    }

    func testDecodeWrongType() {
        expect { try ProtoDecoder.decode(data: Data.init(hex: "7B"), from: 0, to: 4, type: .unknown) as? Int32 }.to(throwError(ProtoParserErrors.notSupportedType))
    }

    func testDecodeBytesWrongSize() throws {
        expect { try ProtoDecoder.decode(data: Data.init(hex: "36172540"), from: 0, to: 10, type: .byte) as? Data }.to(throwError(ProtoParserErrors.wrongData))
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
        expect { try ProtoEncoder.encode(object: 35278, from: 0, to: 5, type: .int32) }.to(throwError(ProtoParserErrors.wrongData))
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
        expect { try ProtoEncoder.encode(object: true, from: 0, to: 2, type: .bool) }.to(throwError(ProtoParserErrors.wrongData))
    }

    func testEncodeEmptyBytes() throws {
        let encoded = try ProtoEncoder.encode(object: Data(), from: 0, to: 0, type: .byte)
        expect(encoded).to(equal(Data()))
    }

    func testEncodePartBytes() throws {
        expect { try ProtoEncoder.encode(object: Data(hex: "36172540"), from: 0, to: 1, type: .byte) }.to(throwError(ProtoParserErrors.wrongData))
    }

    func testEncodeFullBytes() throws {
        let data = Data.init(hex: "36172540")
        let encoded = try ProtoEncoder.encode(object: data, from: 0, to: 4, type: .byte)
        expect(encoded).to(equal(data))
    }

    func testEncodeFromNotZeroBytes() throws {
        let data = Data.init(hex: "2540")
        let encoded = try ProtoEncoder.encode(object: data, from: 2, to: 4, type: .byte)
        expect(encoded).to(equal(Data.init(hex: "2540")))
    }

    func testEncodeBytesWrongSize() throws {
        expect { try ProtoEncoder.encode(object: Data(hex: "36172540"), from: 0, to: 15, type: .byte) }.to(throwError(ProtoParserErrors.wrongData))
    }

    func testEncodeWrongType() {
        expect { try ProtoEncoder.encode(object: 123, from: 0, to: 4, type: .unknown) }.to(throwError(ProtoParserErrors.notSupportedType))
    }
}
