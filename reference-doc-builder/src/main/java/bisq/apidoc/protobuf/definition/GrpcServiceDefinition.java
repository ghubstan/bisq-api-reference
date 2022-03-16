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

import java.util.Map;
import java.util.Objects;

/**
 * A GrpcServiceDefinition is a group of protobuf RpcServiceDefinitions.
 */
public final class GrpcServiceDefinition implements ProtobufDefinition {

    private final String name;
    private final Map<String, RpcMethodDefinition> rpcMethodDefinitions;
    private final String description;

    public GrpcServiceDefinition(String name,
                                 Map<String, RpcMethodDefinition> rpcMethodDefinitions,
                                 String description) {
        this.name = name;
        this.rpcMethodDefinitions = rpcMethodDefinitions;
        this.description = description;
    }

    public String name() {
        return name;
    }

    public Map<String, RpcMethodDefinition> rpcMethodDefinitions() {
        return rpcMethodDefinitions;
    }

    public String description() {
        return description;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (GrpcServiceDefinition) obj;
        return Objects.equals(this.name, that.name) &&
                Objects.equals(this.rpcMethodDefinitions, that.rpcMethodDefinitions) &&
                Objects.equals(this.description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, rpcMethodDefinitions, description);
    }

    @Override
    public String toString() {
        return "GrpcServiceDefinition[" +
                "name=" + name + ", " +
                "rpcMethodDefinitions=" + rpcMethodDefinitions + ", " +
                "description=" + description + ']';
    }
}
