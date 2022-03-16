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
package bisq.apidoc.protobuf.text;

import java.util.ArrayList;
import java.util.List;

import static bisq.apidoc.protobuf.ProtoParserUtil.*;
import static bisq.apidoc.protobuf.text.TextBlock.PROTOBUF_DEF_TYPE.*;
import static bisq.apidoc.protobuf.text.TextBlock.TEXT_BLOCK_TYPE.*;
import static java.lang.String.format;

/**
 * A raw block of text read from a .proto file, defining a gRPC service,
 * a rpc service, message, enum, block comment or line comment.
 */
public class TextBlock {

    public enum TEXT_BLOCK_TYPE {
        BLOCK_COMMENT,
        IMPORT_STATEMENT,
        LINE_COMMENT,
        OPTION_DECLARATION,
        PACKAGE_DECLARATION,
        PROTO_DEFINITION,
        SYNTAX_DECLARATION
    }

    public enum PROTOBUF_DEF_TYPE {
        ENUM_DECLARATION,
        ENUM_CONSTANT,
        GRPC_SERVICE_DECLARATION,
        MESSAGE_DECLARATION,
        DEPRECATED_MESSAGE_FIELD,
        ONEOF_MESSAGE_DECLARATION,
        MESSAGE_FIELD,
        MESSAGE_MAP_FIELD,
        RESERVED_MESSAGE_FIELD,
        RPC_METHOD_DECLARATION,
        UNKNOWN
    }

    private final String text;
    private final TEXT_BLOCK_TYPE textBlockType;
    private final PROTOBUF_DEF_TYPE protobufDefType;
    private final List<TextBlock> children;

    public TextBlock(String text, TEXT_BLOCK_TYPE textBlockType) {
        this.text = text;
        this.textBlockType = textBlockType;
        this.protobufDefType = textBlockType.equals(PROTO_DEFINITION) ? UNKNOWN : null;
        this.children = textBlockType.equals(PROTO_DEFINITION) ? new ArrayList<>() : null;
        validateConstructorTextParam();
    }

    public TextBlock(String text, PROTOBUF_DEF_TYPE protobufDefType) {
        this(text, protobufDefType, new ArrayList<>());
    }

    public TextBlock(String text, PROTOBUF_DEF_TYPE protobufDefType, List<TextBlock> children) {
        this.text = text;
        this.textBlockType = PROTO_DEFINITION;
        this.protobufDefType = protobufDefType;
        this.children = children;
        validateConstructorTextParam();
    }

    public String getText() {
        return text;
    }

    public TEXT_BLOCK_TYPE getTextBlockType() {
        return this.textBlockType;
    }

    public PROTOBUF_DEF_TYPE getProtobufDefType() {
        return protobufDefType;
    }

    public List<TextBlock> getChildren() {
        return children;
    }

    public void addBlockCommentBlock(TextBlock child) {
        if (!child.getTextBlockType().equals(BLOCK_COMMENT))
            throw new IllegalArgumentException(format("Child TextBlock is not a BLOCK_COMMENT:  %s", text));

        children.add(child);
    }

    public void addLineCommentBlock(TextBlock child) {
        if (!child.getTextBlockType().equals(LINE_COMMENT))
            throw new IllegalArgumentException(format("Child TextBlock is not a LINE_COMMENT:  %s", text));

        children.add(child);
    }

    public void addProtoDefinitionBlock(TextBlock child) {
        if (!child.getTextBlockType().equals(PROTO_DEFINITION))
            throw new IllegalArgumentException(format("Child TextBlock is not a PROTO_DEFINITION:  %s", text));

        children.add(child);
    }

    public String getComment() {
        if (!textBlockType.equals(BLOCK_COMMENT) && !textBlockType.equals(LINE_COMMENT))
            throw new IllegalStateException(format("Text block is not a comment:  %s", text));

        return textBlockType.equals(BLOCK_COMMENT) ? getBlockComment() : getLineComment();
    }

    public String getBlockComment() {
        if (!textBlockType.equals(BLOCK_COMMENT))
            throw new IllegalStateException(format("Text block is not a block comment:  %s", text));

        return findFirstMatch(text, BLOCK_COMMENT_PATTERN).orElseThrow(() ->
                new IllegalStateException(format("Could not comment from text block:  %s", text)));
    }

    public String getLineComment() {
        if (!textBlockType.equals(LINE_COMMENT))
            throw new IllegalStateException(format("Text block is not a line comment:  %s", text));

        return text.substring(text.indexOf("//"));
    }

    public String getProtobufDefinitionName() {
        if (!textBlockType.equals(PROTO_DEFINITION))
            throw new IllegalStateException("TextBlock must be a PROTO_DEFINITION to have a protobuf name.");

        // All protobuf service, message, and enum definition names can be
        // found in the second token of the declaration (first line).
        return text.split(" ")[1].trim();
    }

    public boolean isLicenseBlock() {
        return textBlockType.equals(BLOCK_COMMENT) && hasPattern(text, LICENSE_PATTERN);
    }

    public boolean isCommentBlock() {
        return textBlockType.equals(BLOCK_COMMENT) || textBlockType.equals(LINE_COMMENT);
    }

    public boolean isEnumDeclaration() {
        return textBlockType.equals(PROTO_DEFINITION) && protobufDefType.equals(ENUM_DECLARATION);
    }

    public boolean isEnumConstant() {
        return textBlockType.equals(PROTO_DEFINITION) && protobufDefType.equals(ENUM_CONSTANT);
    }

    public boolean isGrpcServiceDeclaration() {
        return textBlockType.equals(PROTO_DEFINITION) && protobufDefType.equals(GRPC_SERVICE_DECLARATION);
    }

    public boolean isOneOfMessageDeclaration() {
        return textBlockType.equals(PROTO_DEFINITION) && protobufDefType.equals(ONEOF_MESSAGE_DECLARATION);
    }

    public boolean isMessageDeclaration() {
        return textBlockType.equals(PROTO_DEFINITION) && protobufDefType.equals(MESSAGE_DECLARATION);
    }

    public boolean isMessageField() {
        return textBlockType.equals(PROTO_DEFINITION) && protobufDefType.equals(MESSAGE_FIELD);
    }

    public boolean isMessageMapField() {
        return textBlockType.equals(PROTO_DEFINITION) && protobufDefType.equals(MESSAGE_MAP_FIELD);
    }

    public boolean isReservedField() {
        return textBlockType.equals(PROTO_DEFINITION) && protobufDefType.equals(RESERVED_MESSAGE_FIELD);
    }

    public boolean isRpcMethodDeclaration() {
        return textBlockType.equals(PROTO_DEFINITION) && protobufDefType.equals(RPC_METHOD_DECLARATION);
    }

    @Override
    public String toString() {
        return "TextBlock {" + "\n"
                + " textBlockType=" + textBlockType
                + ", protobufDefType=" + protobufDefType + "\n"
                + ", " + text + "\n"
                + '}';
    }

    private void validateConstructorTextParam() {
        if (text == null || text.isBlank())
            throw new IllegalArgumentException("TextBlock constructor's text parameter cannot be null.");
    }
}
