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

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static bisq.apidoc.protobuf.ProtoParserUtil.*;
import static bisq.apidoc.protobuf.text.EnumTextBlockFactory.createEnumTextBlock;
import static bisq.apidoc.protobuf.text.TextBlock.PROTOBUF_DEF_TYPE.*;
import static java.lang.String.format;

@Slf4j
public class MessageTextBlockFactory {

    public static TextBlock createMessageTextBlock(ProtobufFileReader fileReader, String firstLine) {
        StringBuilder enclosingMessageTextBuilder = new StringBuilder(firstLine).append("\n");
        List<TextBlock> childBlocks = new ArrayList<>();

        fileReader.pushBracesLevelStack();
        while (!fileReader.isBracesStackEmpty()) {
            String nextTrimmedLine = fileReader.getNextTrimmedLine(true);
            Optional<TextBlock> appendedLineCommentTextBlock = getAppendedLineCommentBlock(nextTrimmedLine);

            if (isClosingProtobufDefinition.test(nextTrimmedLine)) {
                enclosingMessageTextBuilder.append(nextTrimmedLine).append("\n");
                fileReader.popBracesLevelStack();
            } else if (isComment.test(nextTrimmedLine)) {
                TextBlock commentBlock = fileReader.readCommentTextBlock(nextTrimmedLine);
                enclosingMessageTextBuilder.append(indentedText(commentBlock.getText(), fileReader.getBracesStackSize()));
                childBlocks.add(commentBlock);
            } else if (isEnumDeclaration.test(nextTrimmedLine)) {
                TextBlock enumTextBlock = createEnumTextBlock(fileReader, nextTrimmedLine);
                enclosingMessageTextBuilder.append(indentedText(enumTextBlock.getText(), fileReader.getBracesStackSize()));
                childBlocks.add(enumTextBlock);
            } else if (isReservedFieldDeclaration.test(nextTrimmedLine)) {
                if (appendedLineCommentTextBlock.isPresent()) {
                    childBlocks.add(appendedLineCommentTextBlock.get());
                    enclosingMessageTextBuilder.append(indentedText(appendedLineCommentTextBlock.get().getText(), fileReader.getBracesStackSize()));
                }
                String blockText = getCleanBlockText(nextTrimmedLine, appendedLineCommentTextBlock);
                TextBlock reservedFieldTextBlock = new TextBlock(blockText, RESERVED_MESSAGE_FIELD);
                enclosingMessageTextBuilder.append(indentedText(reservedFieldTextBlock.getText(), fileReader.getBracesStackSize()));
                childBlocks.add(reservedFieldTextBlock);
            } else if (isOneOfMessageFieldDeclaration.test(nextTrimmedLine)) {
                // No map fields, no repeated fields, or repeated oneof blocks.
                TextBlock oneOfFieldTextBlock = createOneOfFieldTextBlock(fileReader, nextTrimmedLine);
                enclosingMessageTextBuilder.append(indentedText(oneOfFieldTextBlock.getText(), fileReader.getBracesStackSize()));
                childBlocks.add(oneOfFieldTextBlock);
            } else if (isMessageFieldDeclaration.test(nextTrimmedLine)) {
                if (isDeprecated.test(nextTrimmedLine)) {
                    if (appendedLineCommentTextBlock.isPresent()) {
                        childBlocks.add(appendedLineCommentTextBlock.get());
                        enclosingMessageTextBuilder.append(indentedText(appendedLineCommentTextBlock.get().getText(), fileReader.getBracesStackSize()));
                    }
                    String blockText = getCleanBlockText(nextTrimmedLine, appendedLineCommentTextBlock);
                    TextBlock deprecatedFieldTextBlock = new TextBlock(blockText, DEPRECATED_MESSAGE_FIELD);
                    enclosingMessageTextBuilder.append(indentedText(deprecatedFieldTextBlock.getText(), fileReader.getBracesStackSize()));
                    childBlocks.add(deprecatedFieldTextBlock);
                } else if (isMessageMapFieldDeclaration.test(nextTrimmedLine)) {
                    // Do a check on map type declaration before returning the TextBlock.
                    // It will throw an exception if <k,v> type declaration is not found in the line.
                    getMapKeyValueTypes(nextTrimmedLine);
                    if (appendedLineCommentTextBlock.isPresent()) {
                        childBlocks.add(appendedLineCommentTextBlock.get());
                        enclosingMessageTextBuilder.append(indentedText(appendedLineCommentTextBlock.get().getText(), fileReader.getBracesStackSize()));
                    }
                    String blockText = getCleanBlockText(nextTrimmedLine, appendedLineCommentTextBlock);
                    TextBlock mapFieldTextBlock = new TextBlock(blockText, MESSAGE_MAP_FIELD);
                    enclosingMessageTextBuilder.append(indentedText(blockText, fileReader.getBracesStackSize()));
                    childBlocks.add(mapFieldTextBlock);
                } else {
                    if (appendedLineCommentTextBlock.isPresent()) {
                        childBlocks.add(appendedLineCommentTextBlock.get());
                        enclosingMessageTextBuilder.append(indentedText(appendedLineCommentTextBlock.get().getText(), fileReader.getBracesStackSize()));
                    }
                    String blockText = getCleanBlockText(nextTrimmedLine, appendedLineCommentTextBlock);
                    TextBlock fieldTextBlock = new TextBlock(blockText, MESSAGE_FIELD);
                    enclosingMessageTextBuilder.append(indentedText(blockText, fileReader.getBracesStackSize()));
                    childBlocks.add(fieldTextBlock);
                }
            } else {
                throw new IllegalStateException(format("Invalid protobuf message component: %s", firstLine));
            }
        }
        var messageTextBlock = new TextBlock(enclosingMessageTextBuilder.toString(), MESSAGE_DECLARATION, childBlocks);
        log.trace(">>>> Message TextBlock Text:\n{}", messageTextBlock.getText());
        return messageTextBlock;
    }

    public static TextBlock createOneOfFieldTextBlock(ProtobufFileReader fileReader, String firstLine) {
        // You can add fields of any type, except map fields and repeated fields.
        // See https://developers.google.com/protocol-buffers/docs/proto3#using_oneof

        // Verify the firstLine is declaring a protobuf oneof message field.
        if (!isOneOfMessageFieldDeclaration.test(firstLine))
            throw new IllegalArgumentException(format("First line is not oneof message declaration: %s", firstLine));

        StringBuilder textBuilder = new StringBuilder(indentedText(firstLine, 0));
        List<TextBlock> childBlocks = new ArrayList<>();
        boolean done = false;
        while (!done) {
            String nextTrimmedLine = fileReader.getNextTrimmedLine(true);

            Optional<TextBlock> appendedLineCommentTextBlock = getAppendedLineCommentBlock(nextTrimmedLine);
            if (appendedLineCommentTextBlock.isPresent()) {
                childBlocks.add(appendedLineCommentTextBlock.get());
                textBuilder.append(indentedText(appendedLineCommentTextBlock.get().getText(), 1));
            }

            if (isClosingProtobufDefinition.test(nextTrimmedLine)) {
                textBuilder.append(indentedText(nextTrimmedLine, 0));
                done = true;  // Do not need curlies stack for oneof message case.
            } else if (isComment.test(nextTrimmedLine)) {
                TextBlock commentBlock = fileReader.readCommentTextBlock(nextTrimmedLine);
                textBuilder.append(indentedText(commentBlock.getText(), 1));
                childBlocks.add(commentBlock);
            } else if (isMessageFieldDeclaration.test(nextTrimmedLine)) {
                String blockText = getCleanBlockText(nextTrimmedLine, appendedLineCommentTextBlock);
                TextBlock fieldTextBlock = new TextBlock(blockText, MESSAGE_FIELD);
                textBuilder.append(indentedText(blockText, 1));
                childBlocks.add(fieldTextBlock);
            } else {
                throw new IllegalStateException(format("Invalid text found in protobuf oneof message definition: %s", firstLine));
            }
        }
        return new TextBlock(textBuilder.toString(), ONEOF_MESSAGE_DECLARATION, childBlocks);
    }
}
