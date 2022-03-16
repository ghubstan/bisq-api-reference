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

@SuppressWarnings("ClassCanBeRecord")
public final class RpcMethodDefinition implements ProtobufDefinition {

    private final String name;
    private final MessageDefinition requestMessageDefinition;
    private final MessageDefinition responseMessageDefinition;
    private final String description;

    public RpcMethodDefinition(String name,
                               MessageDefinition requestMessageDefinition,
                               MessageDefinition responseMessageDefinition,
                               String description) {
        this.name = name;
        this.requestMessageDefinition = requestMessageDefinition;
        this.responseMessageDefinition = responseMessageDefinition;
        this.description = description;
    }

    public String name() {
        return name;
    }

    public MessageDefinition requestMessageDefinition() {
        return requestMessageDefinition;
    }

    public MessageDefinition responseMessageDefinition() {
        return responseMessageDefinition;
    }

    public String description() {
        return description;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (RpcMethodDefinition) obj;
        return Objects.equals(this.name, that.name) &&
                Objects.equals(this.requestMessageDefinition, that.requestMessageDefinition) &&
                Objects.equals(this.responseMessageDefinition, that.responseMessageDefinition) &&
                Objects.equals(this.description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, requestMessageDefinition, responseMessageDefinition, description);
    }

    @Override
    public String toString() {
        return "RpcMethodDefinition[" +
                "name=" + name + ", " +
                "requestMessageDefinition=" + requestMessageDefinition + ", " +
                "responseMessageDefinition=" + responseMessageDefinition + ", " +
                "description=" + description + ']';
    }
}
