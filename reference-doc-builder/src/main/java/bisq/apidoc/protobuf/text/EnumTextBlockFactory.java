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
import static bisq.apidoc.protobuf.text.TextBlock.PROTOBUF_DEF_TYPE.ENUM_CONSTANT;
import static bisq.apidoc.protobuf.text.TextBlock.PROTOBUF_DEF_TYPE.ENUM_DECLARATION;
import static java.lang.String.format;

@Slf4j
public class EnumTextBlockFactory {

    public static TextBlock createEnumTextBlock(ProtobufFileReader fileReader, String firstLine) {
        // Verify the firstLine is declaring a protobuf enum.
        if (!isEnumDeclaration.test(firstLine))
            throw new IllegalArgumentException(format("First line is not an enum declaration: %s", firstLine));

        StringBuilder enclosingEnumTextBuilder = new StringBuilder(indentedText(firstLine, 0));
        List<TextBlock> childBlocks = new ArrayList<>();
        fileReader.pushBracesLevelStack();
        boolean done = false;
        while (!done) {
            String nextTrimmedLine = fileReader.getNextTrimmedLine(true);
            Optional<TextBlock> appendedLineCommentTextBlock = getAppendedLineCommentBlock(nextTrimmedLine);
            if (appendedLineCommentTextBlock.isPresent()) {
                childBlocks.add(appendedLineCommentTextBlock.get());
                enclosingEnumTextBuilder.append(indentedText(appendedLineCommentTextBlock.get().getText(), 1));
            }
            if (isClosingProtobufDefinition.test(nextTrimmedLine)) {
                enclosingEnumTextBuilder.append(indentedText(nextTrimmedLine, 0));
                fileReader.bracesLevelStack.pop();
                done = true;
            } else if (isComment.test(nextTrimmedLine)) {
                TextBlock commentBlock = fileReader.readCommentTextBlock(nextTrimmedLine);
                enclosingEnumTextBuilder.append(indentedText(commentBlock.getText(), 1));
                childBlocks.add(commentBlock);
            } else if (isEnumConstantDeclaration.test(nextTrimmedLine)) {
                String blockText = getCleanBlockText(nextTrimmedLine, appendedLineCommentTextBlock);
                TextBlock enumConstant = createEnumConstantTextBlock(blockText);
                enclosingEnumTextBuilder.append(indentedText(enumConstant.getText(), 1));
                childBlocks.add(enumConstant);
            } else {
                throw new IllegalStateException(format("Invalid text found in protobuf enum definition: %s", firstLine));
            }
        }
        enclosingEnumTextBuilder.append("\n");
        var enumTextBlock = new TextBlock(enclosingEnumTextBuilder.toString(), ENUM_DECLARATION, childBlocks);
        log.trace(">>>> Enum TextBlock Text:\n{}", enumTextBlock.getText());
        return enumTextBlock;
    }

    private static TextBlock createEnumConstantTextBlock(String line) {
        try {
            // Sanity check.
            boolean isDeprecatedEnumConstant = isDeprecated.test(line);
            String[] parts = getEnumConstantParts(line, isDeprecatedEnumConstant);
            int constantOrdinal = getEnumConstantOrdinal(parts, isDeprecatedEnumConstant);
            if (constantOrdinal < 0)
                throw new IllegalStateException("Malformed enum constant declaration in line: " + line);

            return new TextBlock(line, ENUM_CONSTANT);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not read enum constant from line: " + line, ex);
        }
    }
}
