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
import static bisq.apidoc.protobuf.text.GrpcServiceTextBlockFactory.createGrpcServiceTextBlock;
import static bisq.apidoc.protobuf.text.MessageTextBlockFactory.createMessageTextBlock;
import static java.lang.String.format;

@Slf4j
public class ProtobufTextBlockFactory {

    public static List<TextBlock> createTextBlocks(ProtobufFileReader fileReader) {
        List<TextBlock> textBlocks = new ArrayList<>(fileReader.readHeaderTextBlocks());
        while (!fileReader.isEndOfFile()) {
            Optional<TextBlock> textBlock = createTextBlock(fileReader);
            textBlock.ifPresent(textBlocks::add);
        }
        return textBlocks;
    }

    private static Optional<TextBlock> createTextBlock(ProtobufFileReader fileReader) {
        try {
            String line = fileReader.getNextTrimmedLine(true);
            if (line == null)
                return Optional.empty();
            else if (isComment.test(line))
                return Optional.of(fileReader.readCommentTextBlock(line));
            else if (isDeclaringProtobufDefinition.test(line))
                return Optional.of(createProtoDefinitionTextBlock(fileReader, line));
            else
                throw new IllegalStateException("Invalid text found in .proto file " + fileReader.getProtobufPath());
        } catch (Exception ex) {
            throw new IllegalStateException("Could not read TextBlock.", ex);
        }
    }

    private static TextBlock createProtoDefinitionTextBlock(ProtobufFileReader fileReader, String firstLine) {
        if (isEnumDeclaration.test(firstLine))
            return createEnumTextBlock(fileReader, firstLine);
        else if (isMessageDeclaration.test(firstLine))
            return createMessageTextBlock(fileReader, firstLine);
        else if (isGrpcServiceDeclaration.test(firstLine))
            return createGrpcServiceTextBlock(fileReader, firstLine);
        else
            throw new IllegalStateException(format("Invalid protobuf declaration: %s", firstLine));
    }
}
