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
import java.util.Map;
import java.util.Objects;

public final class MessageDefinition implements ProtobufDefinition {

    private final String name;
    private final Map<String, EnumDefinition> enums;
    private final Map<String, FieldDefinition> fields;
    private final String description;

    public MessageDefinition(String name,
                             Map<String, EnumDefinition> enums,
                             Map<String, FieldDefinition> fields,
                             String description) {
        this.name = name;
        this.enums = enums;
        this.fields = fields;
        this.description = description;
    }

    public String name() {
        return name;
    }

    public Map<String, EnumDefinition> enums() {
        return enums;
    }

    public Map<String, FieldDefinition> fields() {
        return fields;
    }

    public String description() {
        return description;
    }

    public boolean hasOneOfField() {
        return fields.values().stream().anyMatch(FieldDefinition::isOneOfMessageField);
    }

    public List<FieldDefinition> getOneOfFieldChoices() {
        if (!hasOneOfField())
            return new ArrayList<>();

        FieldDefinition oneOfField = fields.values().stream()
                .filter(FieldDefinition::isOneOfMessageField)
                .findFirst().get();
        return oneOfField.oneOfFieldChoices();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (MessageDefinition) obj;
        return Objects.equals(this.name, that.name) &&
                Objects.equals(this.enums, that.enums) &&
                Objects.equals(this.fields, that.fields) &&
                Objects.equals(this.description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, enums, fields, description);
    }

    @Override
    public String toString() {
        return "MessageDefinition[" +
                "name=" + name + ", " +
                "enums=" + enums + ", " +
                "fields=" + fields + ", " + "\n" +
                "hasOneOfField=" + hasOneOfField() + ", " +
                "description=" + description + ']';
    }
}
