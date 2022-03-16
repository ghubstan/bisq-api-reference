/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */
package bisq.apidoc.protobuf;

/**
 * Valid protobuf v3 field data type definitions from
 * https://developers.google.com/protocol-buffers/docs/proto3#scalar.
 */
public enum ProtobufDataType {

    DOUBLE("double"),
    FLOAT("float"),
    INT32("int32"),
    INT64("int64"),
    UINT64("uint64"),
    SINT32("sint32"),
    SINT64("sint64"),
    FIXED32("fixed32"),
    FIXED64("fixed64"),
    SFIXED32("sfixed32"),
    SFIXED64("sfixed64"),
    BOOL("bool"),
    STRING("string"),
    BYTES("bytes");

    public final String formalName;

    ProtobufDataType(String formalName) {
        this.formalName = formalName;
    }
}
