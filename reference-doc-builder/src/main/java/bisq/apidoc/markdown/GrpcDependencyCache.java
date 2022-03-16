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
package bisq.apidoc.markdown;

import bisq.apidoc.protobuf.definition.*;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static bisq.apidoc.protobuf.ProtoParserUtil.isScalarDataType;
import static java.util.Comparator.comparing;

/**
 * This class caches protobuf message and enum definitions needed by internal navigational links in the API
 * reference doc.  It attempts to find all the required dependencies from the grpcServiceDefinitions constructor
 * argument alone, but recursion is not used to crawl down to the deepest levels, and all bank account payload
 * message types are cached in a hacky way to make sure all of those links work too.
 */
@Slf4j
public class GrpcDependencyCache {

    // Aids debugging:  makes it easy to set break points.
    @SuppressWarnings("unused")
    private final BiPredicate<FieldDefinition, String> isDebuggedField = (field, name) ->
            field.type() != null
                    && !isScalarDataType.test(field.type())
                    && field.type().equals(name);

    // Lists of message and enums to be displayed below the gRPC service blocks.
    // Only enums and messages used by the gRPC services are needed,
    // not all enums and messages parsed from the pb.proto file.
    private final List<MessageDefinition> grpcMessageDependencies;
    private final List<EnumDefinition> grpcEnumDependencies;

    // Maps of ProtobufDefinitions produced by TextBlockParser.
    private final Map<String, EnumDefinition> globalEnumDefinitions;
    private final Map<String, MessageDefinition> globalMessageDefinitions;
    private final Map<String, GrpcServiceDefinition> grpcServiceDefinitions;

    public GrpcDependencyCache(Map<String, EnumDefinition> globalEnumDefinitions,
                               Map<String, MessageDefinition> globalMessageDefinitions,
                               Map<String, GrpcServiceDefinition> grpcServiceDefinitions) {
        this.globalEnumDefinitions = globalEnumDefinitions;
        this.globalMessageDefinitions = globalMessageDefinitions;
        this.grpcServiceDefinitions = grpcServiceDefinitions;
        this.grpcMessageDependencies = new ArrayList<>();
        this.grpcEnumDependencies = new ArrayList<>();
    }

    public GrpcDependencyCache load() {
        // Cache the grpc message dependencies first.
        loadMessages();
        // Now the enum dependencies references by the message dependencies just loaded.
        loadEnums();

        return this;
    }

    public List<MessageDefinition> getGrpcMessageDependencies() {
        return grpcMessageDependencies;
    }

    public List<EnumDefinition> getGrpcEnumDependencies() {
        return grpcEnumDependencies;
    }

    private void loadAllBankAccountPayloadMessages() {
        // Recursion cannot be used to update the dependency cache collection, and some grpc msg dependencies
        // referenced more deeply inside .proto definition structures get skipped (not cached).  This kludge ensures
        // we get all the <Bank>AccountPayload messages into the cache so internal links to them work.
        Predicate<String> isAccountPayloadMessage = (msgName) -> msgName != null && msgName.contains("AccountPayload");
        List<MessageDefinition> accountPayloadMessages = globalMessageDefinitions.values().stream()
                .filter(m -> isAccountPayloadMessage.test(m.name()))
                .collect(Collectors.toList());
        accountPayloadMessages.forEach(this::cacheGrpcMessageDependency);
    }

    private void loadMessages() {
        loadAllBankAccountPayloadMessages();

        grpcServiceDefinitions.values().forEach(serviceDefinition ->
                serviceDefinition.rpcMethodDefinitions().values().forEach(rpcMethodDefinition -> {
                    MessageDefinition requestMessageDefinition = rpcMethodDefinition.requestMessageDefinition();
                    cacheGrpMessageDefinition(requestMessageDefinition);

                    MessageDefinition responseMessageDefinition = rpcMethodDefinition.responseMessageDefinition();
                    cacheGrpMessageDefinition(responseMessageDefinition);
                }));
        grpcMessageDependencies.sort(comparing(MessageDefinition::name));
    }

    private void loadEnums() {
        // Cache the grpc enum dependencies found in the grpc message dependencies.
        grpcMessageDependencies.forEach(m ->
                m.fields().values().forEach(f -> {
                    if (f.isOneOfMessageField()) {
                        log.warn("Dead code?  {} has oneof message field type.", m.name()); // TODO
                    } else if (f.isMapField()) {
                        log.warn("Dead code?  {} has map value type.", m.name());           // TODO
                    } else if (f.type() == null) {
                        log.warn("Dead code?  {} has null field type.", m.name());          // TODO
                    } else if (globalEnumDefinitions.containsKey(f.type())) {
                        EnumDefinition enumDefinition = globalEnumDefinitions.get(f.type());
                        if (!grpcEnumDependencies.contains(enumDefinition)) {
                            grpcEnumDependencies.add(enumDefinition);
                        }
                    }
                }));
        grpcEnumDependencies.sort(comparing(EnumDefinition::name));
    }

    private void cacheGrpMessageDefinition(MessageDefinition messageDefinition) {
        messageDefinition.fields().values().forEach(field -> {
            cacheFieldTypeAsMessage(field);

            // Now iterate the field message's fields and cache those that are messages.
            Optional<MessageDefinition> fieldTypeAsMessage = getMessageDefinition(field.type());
            // A field type can be a message, with its own child fields.
            // (Deep, but we can't use recursion while updating a collection).
            fieldTypeAsMessage.ifPresent(definition ->
                    definition.fields().values().stream()
                            .filter(child -> child.type() != null)
                            .filter(child -> !isScalarDataType.test(child.type()))
                            .forEach(this::cacheFieldTypeAsMessage));
        });
    }

    private void cacheFieldTypeAsMessage(FieldDefinition fieldDefinition) {
        if (fieldDefinition.isMapField()) {
            cacheMapFieldValueType((MapFieldDefinition) fieldDefinition);
        } else {
            Optional<MessageDefinition> fieldAsMessage = getMessageDefinition(fieldDefinition.type());
            fieldAsMessage.ifPresent(message -> {
                cacheGrpcMessageDependency(message);
                if (message.hasOneOfField()) {
                    List<FieldDefinition> choices = message.getOneOfFieldChoices();
                    cacheFieldMessageTypeDependencies(choices);
                } else if (message.fields().size() > 0) {
                    cacheFieldMessageTypeDependencies(message.fields().values().stream().toList());
                }
            });
        }
    }

    private void cacheFieldMessageTypeDependencies(List<FieldDefinition> fields) {
        fields.forEach(f -> {
            if (!f.isMapField() && !f.isOneOfMessageField()) {
                Optional<MessageDefinition> fieldAsMessage = getMessageDefinition(f.type());
                fieldAsMessage.ifPresent(message -> {
                    cacheGrpcMessageDependency(message);
                    if (message.hasOneOfField()) {
                        cacheOneOfFields(message);
                    }
                });
            }
        });
    }

    private void cacheOneOfFields(MessageDefinition messageDefinitionWithOneOfField) {
        List<FieldDefinition> oneOfFieldChoices = messageDefinitionWithOneOfField.getOneOfFieldChoices();
        for (FieldDefinition choice : oneOfFieldChoices) {
            Optional<MessageDefinition> choiceTypeAsMessage = getMessageDefinition(choice.type());
            choiceTypeAsMessage.ifPresent(m -> {
                List<FieldDefinition> fields = m.fields().values().stream().toList();
                fields.forEach(f -> {
                    Optional<MessageDefinition> oneOfMessage = getMessageDefinition(f.type());
                    oneOfMessage.ifPresent(this::cacheGrpcMessageDependency);
                });
            });
        }
    }

    private void cacheMapFieldValueType(MapFieldDefinition mapFieldDefinition) {
        String mapValueType = mapFieldDefinition.valueType();
        Optional<MessageDefinition> mapValueTypeAsMessage = isScalarDataType.test(mapValueType)
                ? Optional.empty()
                : getMessageDefinition(mapValueType);
        if (mapValueTypeAsMessage.isPresent()) {
            log.debug("Cache map field value type as grpc msg dependency: {}", mapValueTypeAsMessage.get());
            cacheGrpcMessageDependency(mapValueTypeAsMessage.get());
        }
    }

    private void cacheGrpcMessageDependency(MessageDefinition messageDefinition) {
        if (!grpcMessageDependencies.contains(messageDefinition))
            grpcMessageDependencies.add(messageDefinition);
    }

    private Optional<MessageDefinition> getMessageDefinition(String fieldType) {
        if (isScalarDataType.test(fieldType))
            return Optional.empty();

        return globalMessageDefinitions.keySet().stream()
                .filter(name -> name.equals(fieldType))
                .map(globalMessageDefinitions::get)
                .findFirst();
    }
}
