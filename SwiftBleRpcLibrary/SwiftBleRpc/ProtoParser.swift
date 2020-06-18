import CryptoSwift
import Foundation
// swiftlint:disable all
/// Type-aliases to "convert" protobuf types to Swift types.
public typealias TYPE_INT32 = Int32
public typealias TYPE_BOOL = Bool
public typealias TYPE_BYTES = Data
public typealias TYPE_FLOAT = Float

/// Type of proto field.
public enum ProtoType: String, CaseIterable {
    case unknown = "TYPE_UNKNOWN"
    case int32 = "TYPE_INT32"
    case byte = "TYPE_BYTES"
    case bool = "TYPE_BOOL"
    case float = "TYPE_FLOAT"
}

// TODO(#67): Add support little endian encoding/decoding.

/// Describes errors appeared during encoding/decoding proto objects.
public enum ProtoParserErrors: Error {
    /// Called when client trying to parse unsupported type.
    case notSupportedType

    /// Called when wrong data sended to parser.
    case wrongData
}

/// Decoder bytes helper.
public class ProtoDecoder {
    /// Decode data to UInt8.
    /// - parameter fromByte: starting byte.
    /// - parameter fromByte: data from which need to convert.
    /// - returns: converted value UInt8.
    private class func decodeUInt8(fromByte: Int, data: Data) -> UInt8 {
        var result: UInt8 = 0
        result <<= 8
        result |= data[fromByte] & 0xFF
        return result
    }

    /// Decode data to Int16.
    /// - parameter fromByte: starting byte.
    /// - parameter data: data from which need to convert.
    /// - returns: converted value Int16.
    private class func decodeInt16(fromByte: Int, data: Data) -> Int16 {
        let subData = data.subdata(in: fromByte..<fromByte + 2)
        return subData.bytes.withUnsafeBufferPointer { pointer in
            (
                pointer.baseAddress!.withMemoryRebound(to: Int16.self, capacity: 1) { pointer in
                    pointer
                }
            )
        }.pointee
    }

    /// Decode data to Int32.
    /// - parameter fromByte: starting byte.
    /// - parameter data: data from which need to convert.
    /// - returns: converted value Int32.
    private class func decodeInt32(fromByte: Int, data: Data) -> Int32 {
        return data.subdata(in: fromByte..<fromByte + 4).bytes.withUnsafeBufferPointer { pointer in
            (
                pointer.baseAddress!.withMemoryRebound(to: Int32.self, capacity: 1) { pointer in
                    pointer
                }
            )
        }.pointee
    }

    /// Decode data to Bool.
    /// - parameter fromByte: starting byte.
    /// - parameter data: data from which need to convert.
    /// - returns: converted value Bool.
    private class func decodeBool(fromByte: Int, data: Data) -> Bool {
        return ProtoDecoder.decodeUInt8(fromByte: fromByte, data: data) != 0
    }

    /// Decode data to Float.
    /// - parameter fromByte: starting byte.
    /// - parameter data: data from which need to convert.
    /// - returns: converted value Float.
    private class func decodeFloat(fromByte: Int, data: Data) -> Float {
        return Float(data.subdata(in: fromByte..<fromByte + 4).bytes.withUnsafeBufferPointer { pointer in
            (
                pointer.baseAddress!.withMemoryRebound(to: Int32.self, capacity: 1) { pointer in
                    pointer
                }
            )
        }.pointee)
    }

    /// Decode data to output object.
    /// - parameter data: data which need to be converted.
    /// - parameter from: from byte inside data.
    /// - parameter to: to byte (actually to calculate length).
    /// - parameter type: the type of output's object data.
    /// - returns: converted object.
    public class func decode(data: Data, from: Int, to: Int, type: ProtoType?) throws -> Any {
        guard let type = type else {
            throw ProtoParserErrors.notSupportedType
        }
        if to > data.count {
            throw ProtoParserErrors.wrongData
        }
        switch type {
        case .int32:
            if to - from == 1 {
                return Int32(ProtoDecoder.decodeUInt8(fromByte: from, data: data))
            } else if to - from == 2 {
                return Int32(ProtoDecoder.decodeInt16(fromByte: from, data: data))
            } else if to - from == 4 {
                return Int32(ProtoDecoder.decodeInt32(fromByte: from, data: data))
            } else {
                throw ProtoParserErrors.wrongData
            }
        case .byte:
            return data.subdata(in: from..<to)
        case .bool:
            if to - from != 1 {
                throw ProtoParserErrors.wrongData
            }
            return ProtoDecoder.decodeBool(fromByte: from, data: data)
        case .float:
            return Float(ProtoDecoder.decodeFloat(fromByte: from, data: data))
        default:
            throw ProtoParserErrors.notSupportedType
        }
    }
}

/// Encoder bytes helper.
public class ProtoEncoder {
    /// Encode any object to data.
    /// - parameter object: any object to convert.
    /// - parameter from: from byte inside data.
    /// - parameter to: to byte (actually to calculate lenth).
    /// - parameter type: the type of output's object data.
    /// - returns: converted Data.
    public class func encode(object: Any, from: Int, to: Int, type: ProtoType?) throws -> Data {
        guard let type = type else {
            throw ProtoParserErrors.notSupportedType
        }
        switch type {
        case .int32, .float:
            if to - from > 4 {
                throw ProtoParserErrors.wrongData
            }
            var valuePointer = object
            let data = NSData(bytes: &valuePointer, length: to - from)
            return data as Data
        case .byte:
            let data = object as! Data
            if to - from != data.count {
                throw ProtoParserErrors.wrongData
            }
            return data
        case .bool:
            if to - from != 1 {
                throw ProtoParserErrors.wrongData
            }
            var valuePointer = object
            let data = NSData(bytes: &valuePointer, length: 1)
            return data as Data
        default:
            throw ProtoParserErrors.notSupportedType
        }
    }
}
