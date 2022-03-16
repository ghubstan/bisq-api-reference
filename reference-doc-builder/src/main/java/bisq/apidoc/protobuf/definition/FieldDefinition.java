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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class FieldDefinition implements ProtobufDefinition {

    protected final boolean isRepeated;
    protected final String type;
    protected final String name;
    protected final int fieldNumber;
    protected final String description;
    protected final boolean isDeprecated; // Only fields can be @Deprecated, not messages nor services.
    protected boolean isReserved;

    // Oneof fields cannot be repeated:  https://developers.google.com/protocol-buffers/docs/proto3#oneof
    private final List<FieldDefinition> oneOfFieldChoices;

    /**
     * Constructor for "One Of" message fields.
     */
    public FieldDefinition(List<FieldDefinition> oneOfFieldChoices,
                           int fieldNumber,
                           String description,
                           boolean isDeprecated) {
        this(false, null, null, fieldNumber, description, isDeprecated);
        this.oneOfFieldChoices.addAll(oneOfFieldChoices);
    }

    /**
     * Constructor for reserved protobuf message fields, e.g., 'reserved 3;'.
     */
    public FieldDefinition(int fieldNumber,
                           String description) {
        this(false, null, null, fieldNumber, description, false);
        this.isReserved = true;
    }

    /**
     * Constructor for standard fields, but may be used by the MapFieldDefinition subclass.
     */
    public FieldDefinition(boolean isRepeated,
                           String type,
                           String name,
                           int fieldNumber,
                           String description,
                           boolean isDeprecated) {
        this.isRepeated = isRepeated;
        this.type = type;
        this.name = name;
        this.fieldNumber = fieldNumber;
        this.description = description;
        this.isDeprecated = isDeprecated;
        this.oneOfFieldChoices = new ArrayList<>();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Getter method names are not prefixed by 'get' to conform to other implicate 'record' getters in this package.
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public boolean isRepeated() {
        return this.isRepeated;
    }

    public String type() {
        return this.type;
    }

    public String name() {
        return this.name;
    }

    public int fieldNumber() {
        return this.fieldNumber;
    }

    public String description() {
        return this.description;
    }

    public boolean isDeprecated() {
        return this.isDeprecated;
    }

    public List<FieldDefinition> oneOfFieldChoices() {
        return this.oneOfFieldChoices;
    }

    public boolean isReserved() {
        return this.isReserved;
    }

    public boolean isOneOfMessageField() {
        return this.oneOfFieldChoices.size() > 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FieldDefinition that = (FieldDefinition) o;
        return isRepeated == that.isRepeated
                && fieldNumber == that.fieldNumber
                && isDeprecated == that.isDeprecated
                && isReserved == that.isReserved
                && type.equals(that.type)
                && name.equals(that.name)
                && description.equals(that.description)
                && oneOfFieldChoices.equals(that.oneOfFieldChoices);
    }

    @Override
    public int hashCode() {
        return Objects.hash(isRepeated,
                type,
                name,
                fieldNumber,
                description,
                isDeprecated,
                isReserved,
                oneOfFieldChoices);
    }

    @Override
    public String toString() {
        return "FieldDefinition{" +
                "isRepeated=" + isRepeated +
                ", type='" + type + '\'' +
                ", name='" + name + '\'' +
                ", fieldNumber=" + fieldNumber +
                ", description='" + description + '\'' +
                ", isDeprecated=" + isDeprecated +
                ", isReserved=" + isReserved +
                ", oneOfFieldChoices=" + oneOfFieldChoices +
                '}';
    }
}
