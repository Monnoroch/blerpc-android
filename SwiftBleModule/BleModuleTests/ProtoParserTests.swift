//
//  ProtoParserTests.swift
//  AURA BLE ModuleTests
//
//  Created by Vladyslav Semenchenko on 2/18/19.
//  Copyright © 2019 AURA. All rights reserved.
//

import XCTest
import Nimble
@testable import BleModule

class ProtoParserTests: XCTestCase {
    
    func testDecodeUInt8() {
        let decoded = ProtoDecoder.decodeUInt8(fromByte: 0, data: Data.init(hex: "7B"))
        expect(decoded).to(equal(123))
    }
    
    func testDecodeInt() {
        let decoded = ProtoDecoder.decodeInt(fromByte: 0, data: Data.init(bytes: [123, 0]))
        expect(decoded).to(equal(123))
    }
    
    func testDecodeUInt16() {
        let decoded1 = ProtoDecoder.decodeUInt16(fromByte: 0, data: Data.init(bytes: [123, 0]))
        expect(decoded1).to(equal(123))
        
        let decoded2 = ProtoDecoder.decodeUInt16(fromByte: 0, data: Data.init(hex: "FFFFFFFFFFFFFF85"))
        expect(decoded2).to(equal(65535))
    }
    
    func testDecodeInt16() {
        let decoded = ProtoDecoder.decodeInt16(fromByte: 0, data: Data.init(bytes: [123, 0]))
        expect(decoded).to(equal(123))
    }
    
    func testDecodeUInt32() {
        let decoded = ProtoDecoder.decodeUInt32(fromByte: 0, data: Data.init(bytes: [123, 0, 0, 0]))
        expect(decoded).to(equal(123))
    }
    
    func testDecodeInt32() {
        let decoded = ProtoDecoder.decodeInt32(fromByte: 0, data: Data.init(bytes: [123, 0, 0, 0]))
        expect(decoded).to(equal(123))
    }
    
    func testDecodeString() {
        let decoded = ProtoDecoder.decodeString(fromByte: 0, toByte: 4, data: "test_decode_string".data(using: .utf8)!)
        expect(decoded).to(equal("test_"))
    }
    
    func testDecodeBool() {
        let decoded = ProtoDecoder.decodeBool(fromByte: 0, data: Data.init(hex: "1"))
        expect(decoded).to(equal(true))
    }
    
    func testEncodeInt() {
        let encoded = ProtoEncoder.encodeInt(value: Int16(123))
        expect(encoded).to(equal(Data.init(bytes: [123, 0])))
    }
    
}
