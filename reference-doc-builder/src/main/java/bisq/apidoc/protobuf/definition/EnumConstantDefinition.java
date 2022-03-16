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

public final class EnumConstantDefinition implements ProtobufDefinition {

    private final String name;
    private final int value;
    private final String description;
    private final boolean isDeprecated;

    public EnumConstantDefinition(String name,
                                  int value,
                                  String description,
                                  boolean isDeprecated) {
        this.name = name;
        this.value = value;
        this.description = description;
        this.isDeprecated = isDeprecated;
    }

    public String name() {
        return name;
    }

    public int value() {
        return value;
    }

    public String description() {
        return description;
    }

    public boolean isDeprecated() {
        return isDeprecated;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (EnumConstantDefinition) obj;
        return Objects.equals(this.name, that.name) &&
                this.value == that.value &&
                Objects.equals(this.description, that.description) &&
                this.isDeprecated == that.isDeprecated;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, value, description, isDeprecated);
    }

    @Override
    public String toString() {
        return "EnumConstantDefinition[" +
                "name=" + name + ", " +
                "value=" + value + ", " +
                "description=" + description + ", " +
                "isDeprecated=" + isDeprecated + ']';
    }
}
