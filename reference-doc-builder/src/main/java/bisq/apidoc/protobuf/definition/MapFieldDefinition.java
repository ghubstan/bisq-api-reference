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
package bisq.apidoc.protobuf.definition;

import java.util.Objects;

public class MapFieldDefinition extends FieldDefinition implements ProtobufDefinition {

    // Map fields are not repeatable.
    private final String keyType;
    private final String valueType;

    public MapFieldDefinition(String keyType,
                              String valueType,
                              String name,
                              int fieldNumber,
                              String description,
                              boolean isDeprecated) {
        super(false, null, name, fieldNumber, description, isDeprecated);
        this.keyType = keyType;
        this.valueType = valueType;
    }

    public String keyType() {
        return keyType;
    }

    public String valueType() {
        return valueType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MapFieldDefinition)) return false;
        if (!super.equals(o)) return false;
        MapFieldDefinition that = (MapFieldDefinition) o;
        return keyType.equals(that.keyType) && valueType.equals(that.valueType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), keyType, valueType);
    }

    @Override
    public String toString() {
        return "MapFieldDefinition{" +
                "name='" + name + '\'' +
                ", fieldNumber=" + fieldNumber +
                ", description='" + description + '\'' +
                ", isDeprecated=" + isDeprecated +
                ", keyType='" + keyType + '\'' +
                ", valueType='" + valueType + '\'' +
                '}';
    }
}
