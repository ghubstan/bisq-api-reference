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

import bisq.apidoc.protobuf.text.TextBlock;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

import static bisq.apidoc.protobuf.ProtoParserUtil.*;
import static bisq.apidoc.protobuf.text.TextBlock.TEXT_BLOCK_TYPE.*;
import static java.lang.String.format;

/**
 * Transforms lists of TextBlocks into ProtobufDefinition objects used to generate the final API doc markdown file.
 */
@SuppressWarnings({"rawtypes", "unchecked", "OptionalUsedAsFieldOrParameterType"})
@Slf4j
public class TextBlockParser {

    private final Map<String, EnumDefinition> globalEnumDefinitions = new TreeMap<>();
    private final Map<String, MessageDefinition> globalMessageDefinitions = new TreeMap<>();
    private final Map<String, GrpcServiceDefinition> grpcServiceDefinitions = new TreeMap<>();

    private final List<TextBlock> pbProtoTextBlocks;
    private final List<TextBlock> grpcProtoTextBlocks;
    @SuppressWarnings("FieldCanBeLocal")
    private final boolean failOnMissingDocumentation;

    public TextBlockParser(List<TextBlock> pbProtoTextBlocks, List<TextBlock> grpcProtoTextBlocks, boolean failOnMissingDocumentation) {
        this.pbProtoTextBlocks = pbProtoTextBlocks;
        this.grpcProtoTextBlocks = grpcProtoTextBlocks;
        this.failOnMissingDocumentation = failOnMissingDocumentation;
    }

    public void parse() {
        // The pb.proto TextBlocks must be parsed first because grpc.proto definitions depend on them.
        parse(pbProtoTextBlocks);
        // The grpc.proto enum and message TextBlocks must be parsed before the grpc service TextBlocks.
        parse(grpcProtoTextBlocks, false);
        // The grpc.proto enum and message TextBlocks have been cached for lookup, now parse the grpc service blocks.
        parse(grpcProtoTextBlocks, true);
    }

    public Map<String, EnumDefinition> getGlobalEnumDefinitions() {
        return globalEnumDefinitions;
    }

    public Map<String, MessageDefinition> getGlobalMessageDefinitions() {
        return globalMessageDefinitions;
    }

    public Map<String, GrpcServiceDefinition> getGrpcServiceDefinitions() {
        return grpcServiceDefinitions;
    }

    private void parse(List<TextBlock> textBlocks) {
        parse(textBlocks, false);
    }

    private void parse(List<TextBlock> textBlocks, boolean onlyParseGrpcServiceTextBlocks) {
        for (int i = 0; i < textBlocks.size(); i++) {
            TextBlock current = textBlocks.get(i);

            if (isHeaderTextBlock(current))
                continue; // skip header line

            Optional<TextBlock> descriptionTextBlock;

            if (!onlyParseGrpcServiceTextBlocks && current.isEnumDeclaration()) {
                descriptionTextBlock = getProtoDescriptionTextBlock(current, textBlocks.get(i - 1));
                parseEnumTextBlock(current, descriptionTextBlock);
            } else if (!onlyParseGrpcServiceTextBlocks && current.isMessageDeclaration()) {
                descriptionTextBlock = getProtoDescriptionTextBlock(current, textBlocks.get(i - 1));
                parseMessageTextBlock(current, descriptionTextBlock);
            } else if (onlyParseGrpcServiceTextBlocks && current.isGrpcServiceDeclaration()) {
                descriptionTextBlock = getProtoDescriptionTextBlock(current, textBlocks.get(i - 1));
                parseGrpcServiceTextBlock(current, descriptionTextBlock);
            }

            // Else no-op:  nested comments, rpc methods, messages, etc.
            // are consumed by high level protobuf TextBlock parsers.
        }
    }

    private Optional<TextBlock> getProtoDescriptionTextBlock(TextBlock current, TextBlock previous) {
        return current.getTextBlockType().equals(PROTO_DEFINITION) && previous.isCommentBlock()
                ? Optional.of(previous)
                : Optional.empty();
    }

    private void parseEnumTextBlock(TextBlock enumTextBlock,
                                    Optional<TextBlock> descriptionTextBlock) {
        EnumDefinition enumDefinition = getEnumDefinition(enumTextBlock, descriptionTextBlock, true);
        globalEnumDefinitions.put(enumDefinition.name(), enumDefinition);
    }

    private void parseMessageTextBlock(TextBlock messageTextBlock,
                                       Optional<TextBlock> descriptionTextBlock) {
        String messageName = messageTextBlock.getProtobufDefinitionName();
        String description = getDescription(descriptionTextBlock);
        Map<String, EnumDefinition> enums = new LinkedHashMap();
        Map<String, FieldDefinition> fields = new LinkedHashMap();

        List<TextBlock> children = messageTextBlock.getChildren();
        for (int i = 0; i < children.size(); i++) {

            ProtoAndDescriptionPair pair = new ProtoAndDescriptionPair(children, i);
            // Bump children iteration idx if we just consumed two TextBlock list elements.
            if (pair.optionalDescription.isPresent())
                i++;

            String fieldDescription = getDescription(pair.optionalDescription);
            if (pair.proto.isEnumDeclaration()) {
                EnumDefinition enumDefinition = getEnumDefinition(pair.proto, pair.optionalDescription, false);
                enums.put(enumDefinition.name(), enumDefinition);
            } else if (pair.proto.isMessageMapField()) {
                MapFieldDefinition mapFieldDefinition = parseMapFieldDefinition(pair.proto.getText());
                fields.put(mapFieldDefinition.name(), mapFieldDefinition);
            } else if (pair.proto.isOneOfMessageDeclaration()) {
                FieldDefinition oneOfFieldDefinition = parseOneOfFieldDefinition(pair.proto, fieldDescription);
                fields.put(ONEOF_MESSAGE, oneOfFieldDefinition);
            } else if (pair.proto.isReservedField()) {
                FieldDefinition fieldDefinition = parseReservedFieldDefinition(pair.proto.getText(), fieldDescription);
                // TODO calculate and lookup reserved field "name" for markdown generation.
                fields.put("reserved_field_number_" + fieldDefinition.fieldNumber(), fieldDefinition);
            } else if (pair.proto.isMessageField()) {
                FieldDefinition fieldDefinition = parseStandardFieldDefinition(pair.proto.getText(), fieldDescription);
                fields.put(fieldDefinition.name(), fieldDefinition);
            }
        }
        MessageDefinition messageDefinition = new MessageDefinition(messageName, enums, fields, description);
        globalMessageDefinitions.put(messageName, messageDefinition);
    }

    private void parseGrpcServiceTextBlock(TextBlock serviceTextBlock,
                                           Optional<TextBlock> descriptionTextBlock) {
        String serviceName = serviceTextBlock.getProtobufDefinitionName();
        String description = getDescription(descriptionTextBlock);
        Map<String, RpcMethodDefinition> rpcMethods = new LinkedHashMap();

        List<TextBlock> children = serviceTextBlock.getChildren();
        for (int i = 0; i < children.size(); i++) {
            ProtoAndDescriptionPair pair = new ProtoAndDescriptionPair(children, i);
            // Bump children iteration idx if we just consumed two TextBlock list elements.
            if (pair.optionalDescription.isPresent())
                i++;

            String rpcMethodDescription = getDescription(pair.optionalDescription);
            if (pair.proto.isRpcMethodDeclaration()) {
                RpcMethodDefinition rpcMethodDefinition = getRpcMethodDefinition(pair.proto, rpcMethodDescription);
                rpcMethods.put(rpcMethodDefinition.name(), rpcMethodDefinition);
            }
        }
        GrpcServiceDefinition serviceDefinition = new GrpcServiceDefinition(serviceName, rpcMethods, description);
        grpcServiceDefinitions.put(serviceName, serviceDefinition);
    }

    private RpcMethodDefinition getRpcMethodDefinition(TextBlock rpcMethodTextBlock, String description) {
        String methodName = rpcMethodTextBlock.getProtobufDefinitionName();
        String line = rpcMethodTextBlock.getText();
        String[] parts = rpcMethodTextBlock.getText().split(" ");
        String requestName = findFirstMatch(parts[2], PARENTHESES_PATTERN).orElseThrow(() ->
                new IllegalStateException(format("Could not parse rpc request method name from line:  %s", line)));
        MessageDefinition requestMessage = findMessageDefinition(requestName);
        String responseName = findFirstMatch(parts[4], PARENTHESES_PATTERN).orElseThrow(() ->
                new IllegalStateException(format("Could not parse rpc response method name from line:   %s", line)));
        MessageDefinition responseMessage = findMessageDefinition(responseName);
        return new RpcMethodDefinition(methodName, requestMessage, responseMessage, description);
    }

    private FieldDefinition parseOneOfFieldDefinition(TextBlock oneOfFieldTextBlock, String description) {
        List<FieldDefinition> fieldChoices = new ArrayList<>();
        List<TextBlock> children = oneOfFieldTextBlock.getChildren();
        for (int i = 0; i < children.size(); i++) {
            ProtoAndDescriptionPair pair = new ProtoAndDescriptionPair(children, i);
            // Bump children iteration idx if we just consumed two TextBlock list elements.
            if (pair.optionalDescription.isPresent())
                i++;

            if (!pair.proto.isMessageField())
                throw new IllegalStateException(format("Could not parse oneof msg field definition block:  %s", pair.proto));

            String fieldChoiceDescription = getDescription(pair.optionalDescription);
            FieldDefinition fieldDefinition = parseStandardFieldDefinition(pair.proto.getText(), fieldChoiceDescription);
            fieldChoices.add(fieldDefinition);
        }
        boolean isDeprecatedFieldChoice = hasDeprecationOption.test(oneOfFieldTextBlock.getText());
        return new FieldDefinition(fieldChoices,
                -1,
                description,
                isDeprecatedFieldChoice);
    }

    private MapFieldDefinition parseMapFieldDefinition(String line) {
        try {
            Optional<String> keyValueDataTypes = findFirstMatch(line, MAP_KV_TYPES_PATTERN);
            if (keyValueDataTypes.isPresent()) {
                String[] kvTypes = keyValueDataTypes.get().split(",");
                String keyType = kvTypes[0].trim();
                String valueType = kvTypes[1].trim();
                boolean isDeprecated = hasDeprecationOption.test(line);
                String[] nameAndFieldNumberParts = getMapFieldNameAndFieldNumberParts(line, isDeprecated);
                String name = nameAndFieldNumberParts[0].trim();
                int fieldNumber = Integer.parseInt(nameAndFieldNumberParts[1].trim());
                return new MapFieldDefinition(keyType, valueType, name, fieldNumber, "todo", isDeprecated);
            } else {
                throw new RuntimeException(format("Could not parse msg map field kv data types at line:  %s", line));
            }
        } catch (ArrayIndexOutOfBoundsException ex) {
            throw new RuntimeException(format("Could not parse msg field definition at line:  %s", line), ex);
        } catch (NumberFormatException ex) {
            throw new RuntimeException(format("Could not parse msg field number at line:  %s", line), ex);
        }
    }

    private FieldDefinition parseReservedFieldDefinition(String line, String fieldDescription) {
        if (!isReservedFieldDeclaration.test(line))
            throw new IllegalArgumentException("Line argument does not represent a reserved field declaration: " + line);

        // Reserved fields have no name, just the 'reserved'
        // keyword followed by the field number being reserved.
        String fieldNumberPart = stripSemicolon(line.split(" ")[1]).trim();
        int fieldNumber = Integer.parseInt(fieldNumberPart);
        return new FieldDefinition(fieldNumber, fieldDescription);
    }

    private FieldDefinition parseStandardFieldDefinition(String line, String fieldDescription) {
        try {
            String[] parts = stripSemicolon(line).split(" ");
            boolean isRepeated = parts[0].trim().equals("repeated");
            String dataType = isRepeated ? parts[1] : parts[0];
            String name = isRepeated ? parts[2] : parts[1];
            int fieldNumber = isRepeated ? Integer.parseInt(parts[4]) : Integer.parseInt(parts[3]);
            boolean isDeprecated = hasDeprecationOption.test(line);
            return new FieldDefinition(isRepeated, dataType, name, fieldNumber, fieldDescription, isDeprecated);
        } catch (ArrayIndexOutOfBoundsException ex) {
            throw new RuntimeException(format("Could not parse msg field definition at line:  %s", line), ex);
        } catch (NumberFormatException ex) {
            throw new RuntimeException(format("Could not parse msg field number at line:  %s", line), ex);
        }
    }

    private EnumDefinition getEnumDefinition(TextBlock enumTextBlock,
                                             Optional<TextBlock> descriptionTextBlock,
                                             boolean isGlobal) {
        String enumName = enumTextBlock.getProtobufDefinitionName();
        String description = getDescription(descriptionTextBlock);
        Map<String, EnumConstantDefinition> constants = new LinkedHashMap();
        List<TextBlock> children = enumTextBlock.getChildren();
        for (int i = 0; i < children.size(); i++) {
            ProtoAndDescriptionPair pair = new ProtoAndDescriptionPair(children, i);
            // Bump children iteration idx if we just consumed two TextBlock list elements.
            if (pair.optionalDescription.isPresent())
                i++;

            String constantDescription = getDescription(pair.optionalDescription);
            EnumConstantDefinition constantDefinition = parseEnumConstantDefinition(
                    pair.proto.getText(),
                    constantDescription);
            constants.put(constantDefinition.name(), constantDefinition);
        }
        return new EnumDefinition(enumName, constants, description, isGlobal);
    }

    private EnumConstantDefinition parseEnumConstantDefinition(String line, String description) {
        try {
            boolean isDeprecated = hasDeprecationOption.test(line);
            String[] parts = getEnumConstantParts(line, isDeprecated);
            String constant = parts[0].trim();
            int ordinal = getEnumConstantOrdinal(parts, isDeprecated);
            return new EnumConstantDefinition(constant, ordinal, description, isDeprecated);
        } catch (ArrayIndexOutOfBoundsException ex) {
            throw new IllegalStateException(format("Could not parse enum constant definition at line:  %s", line), ex);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException(format("Could not parse enum constant value at line:  %s", line), ex);
        }
    }

    private String getDescription(Optional<TextBlock> optionalDescription) {
        if (optionalDescription.isPresent()) {
            TextBlock textBlock = optionalDescription.get();
            String rawComment = textBlock.getText();
            return textBlock.getTextBlockType().equals(LINE_COMMENT)
                    ? toCleanLineComment.apply(rawComment)
                    : toCleanBlockComment.apply(rawComment);
        } else {
            return "";
        }
    }

    private MessageDefinition findMessageDefinition(String name) {
        if (globalMessageDefinitions.containsKey(name))
            return globalMessageDefinitions.get(name);
        else
            throw new IllegalStateException(
                    format("Could not find '%s' protobuf message in pb.proto or grpc.proto.", name));
    }

    @SuppressWarnings("unused")
    private EnumDefinition findEnumDefinition(String name) {
        if (globalEnumDefinitions.containsKey(name))
            return globalEnumDefinitions.get(name);
        else
            throw new IllegalStateException(
                    format("Could not find '%s' protobuf enum in pb.proto or grpc.proto.", name));
    }

    private boolean isHeaderTextBlock(TextBlock textBlock) {
        return textBlock.isLicenseBlock()
                || textBlock.getTextBlockType().equals(SYNTAX_DECLARATION)
                || textBlock.getTextBlockType().equals(PACKAGE_DECLARATION)
                || textBlock.getTextBlockType().equals(IMPORT_STATEMENT)
                || textBlock.getTextBlockType().equals(OPTION_DECLARATION);
    }

    /**
     * Container class for protobuf definition TextBlock paired with its optional comment TextBlock.
     */
    private static class ProtoAndDescriptionPair {
        final List<TextBlock> siblings;
        final Optional<TextBlock> optionalDescription;
        final TextBlock proto;

        public ProtoAndDescriptionPair(List<TextBlock> siblings, int currentSiblingIdx) {
            this.siblings = siblings;
            this.optionalDescription = siblings.get(currentSiblingIdx).isCommentBlock()
                    ? Optional.of(siblings.get(currentSiblingIdx))
                    : Optional.empty();
            this.proto = optionalDescription.isPresent()
                    ? siblings.get(currentSiblingIdx + 1)
                    : siblings.get(currentSiblingIdx);
        }
    }
}
