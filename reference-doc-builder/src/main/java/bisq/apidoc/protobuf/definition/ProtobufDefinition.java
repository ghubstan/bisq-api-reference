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

/**
 * Marker interface for pojos -- much easier to transform into markdown context than chunks of text.
 */
public interface ProtobufDefinition {

    String name();

    String description();

    default boolean hasDescription() {
        return this.description() != null && !this.description().isBlank();
    }

    default boolean isEnum() {
        return this instanceof EnumDefinition;
    }

    default boolean isMessage() {
        return this instanceof MessageDefinition;
    }

    default boolean isMapField() {
        return this instanceof MapFieldDefinition;
    }
}
