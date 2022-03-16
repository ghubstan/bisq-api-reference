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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import static bisq.apidoc.protobuf.ProtoParserUtil.*;
import static bisq.apidoc.protobuf.text.TextBlock.TEXT_BLOCK_TYPE.*;
import static java.lang.String.format;

@Slf4j
public class ProtobufFileReader {

    // Used to track nested curly braces in nested structures in .proto files.
    @SuppressWarnings("rawtypes")
    protected final Stack bracesLevelStack = new Stack();

    protected final Path protobufPath;
    protected final RandomAccessFile randomAccessFile;

    public ProtobufFileReader(Path protobufPath) {
        this.protobufPath = protobufPath;
        randomAccessFile = open();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isBracesStackEmpty() {
        return bracesLevelStack.empty();
    }

    public int getBracesStackSize() {
        return bracesLevelStack.size();
    }

    public int popBracesLevelStack() {
        return (int) bracesLevelStack.pop();
    }

    public void pushBracesLevelStack() {
        //noinspection unchecked
        bracesLevelStack.push(bracesLevelStack.size());
    }

    public Path getProtobufPath() {
        return protobufPath;
    }

    public String getNextTrimmedLine() {
        return getNextTrimmedLine(false);
    }

    public String getNextTrimmedLine(boolean skipBlankLines) {
        try {
            if (isEndOfFile())
                throw new IllegalStateException("Reached end of random access file.");

            String line = randomAccessFile.readLine();
            if (line == null)
                return null;

            while (skipBlankLines && isBlankLine.test(line)) {
                line = randomAccessFile.readLine();
                if (line == null)
                    return null;
            }


            return line.trim();
        } catch (IOException ex) {
            throw new IllegalStateException("Error consuming blank lines in " + protobufPath, ex);
        }
    }

    public List<TextBlock> readHeaderTextBlocks() {
        try {
            List<TextBlock> textBlocks = new ArrayList<>();
            while (!isEndOfFile()) {
                long lastFilePtr = getFilePointer();
                String line = getNextTrimmedLine(true);
                if (isComment.test(line))
                    textBlocks.add(readCommentTextBlock(line));
                else if (isSyntaxDeclaration.test(line))
                    textBlocks.add(new TextBlock(line, SYNTAX_DECLARATION));
                else if (isPackageDeclaration.test(line))
                    textBlocks.add(new TextBlock(line, PACKAGE_DECLARATION));
                else if (isImportStatement.test(line))
                    textBlocks.add(new TextBlock(line, IMPORT_STATEMENT));
                else if (isOptionDeclaration.test(line))
                    textBlocks.add(new TextBlock(line, OPTION_DECLARATION));
                else if (isDeclaringProtobufDefinition.test(line)) {
                    // The .proto file license, syntax, pkg, import, and option TextBlocks have been read.
                    // Move the file ptr back to where it was before reading the first line of the first
                    // protobuf declaration and break out of the loop.
                    randomAccessFile.seek(lastFilePtr);
                    break;
                }
            }
            return textBlocks;
        } catch (IOException ex) {
            throw new IllegalStateException("Could not get random access file ptr.", ex);
        }
    }

    public TextBlock readCommentTextBlock(String firstLine) {
        if (isBlockCommentOpener.test(firstLine))
            return readBlockCommentTextBlock(firstLine);
        else if (isLineComment.test(firstLine))
            return readLineCommentTextBlock(firstLine);
        else
            throw new IllegalStateException(format("Line '%s' is not a comment.", firstLine));
    }

    public RandomAccessFile open() {
        try {
            return new RandomAccessFile(protobufPath.toFile(), "r");
        } catch (FileNotFoundException ex) {
            throw new IllegalArgumentException(protobufPath.getFileName() + " not found.", ex);
        }
    }

    public boolean isEndOfFile() {
        try {
            return getFilePointer() == randomAccessFile.length();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not determine file has been fully read.", ex);
        }
    }

    public void close() {
        try {
            randomAccessFile.close();
        } catch (IOException ex) {
            throw new IllegalStateException(protobufPath.getFileName() + " not closed.", ex);
        }
    }

    public long getFilePointer() {
        try {
            return randomAccessFile.getFilePointer();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not get random access file pointer.", ex);
        }
    }

    private TextBlock readBlockCommentTextBlock(String firstLine) {
        /* A block comment can be single line. */
        if (isBlockCommentCloser.test(firstLine))
            return new TextBlock(firstLine, BLOCK_COMMENT);

        StringBuilder sb = new StringBuilder(firstLine).append("\n");
        while (!isEndOfFile()) {
            String line = getNextTrimmedLine();
            sb.append(line).append("\n");
            if (isBlockCommentCloser.test(line))
                break;
        }
        if (isEndOfFile())
            throw new IllegalStateException(
                    "Reading the comment exhausted the file pointer, protobuf file must be malformed.");

        return new TextBlock(sb.toString(), BLOCK_COMMENT);
    }

    private TextBlock readLineCommentTextBlock(String firstLine) {
        try {
            StringBuilder sb = new StringBuilder(firstLine).append("\n");
            while (true) {
                long savedFilePointer = getFilePointer();
                String line = getNextTrimmedLine(true);
                if (!isLineComment.test(line)) {
                    // Move file ptr back to non-comment line to it is not missed by next getNextTrimmedLine() call.
                    randomAccessFile.seek(savedFilePointer);
                    break;
                } else {
                    sb.append(line).append("\n");
                }
            }
            return new TextBlock(sb.toString(), LINE_COMMENT);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read block comment.", ex);
        }
    }
}
