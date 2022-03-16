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
package bisq.apidoc.protobuf;

import bisq.apidoc.protobuf.text.TextBlock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static bisq.apidoc.protobuf.text.TextBlock.TEXT_BLOCK_TYPE.LINE_COMMENT;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.stream;

// Note:  Line comments appended to protobuf declarations ending in '{' will not be supported.
// Note:  Single line block comments appended to any protobuf declarations will not be supported.
// None existed in pb.proto as of 2022-02-05.

public class ProtoParserUtil {

    private static final String DEPRECATION_OPTION = "[deprecated = true]";
    private static final String LINE_COMMENT_SLASHES = "//";
    private static final String OPENING_CURLY_BRACE = "{";
    private static final String CLOSING_CURLY_BRACE = "}";

    public static final String ONEOF_MESSAGE = "oneof message";

    // TextBlock content indentation is determined by bracesLevelStack.size().
    static final char INDENT_CHAR = '\t';

    // Regex patterns //

    public static final Pattern BLOCK_COMMENT_PATTERN = Pattern.compile("/\\*([^}]+)\\*/");
    public static final Pattern LICENSE_PATTERN = Pattern.compile("GNU Affero General Public License");
    public static final Pattern MAP_KV_TYPES_PATTERN = Pattern.compile("<([^}]+)>");
    public static final Pattern MESSAGE_FIELD_PATTERN = Pattern.compile("=\\s+\\d+");
    public static final Pattern PARENTHESES_PATTERN = Pattern.compile("\\(([^}]+)\\)");

    // .proto file syntax, package, options (at top of file) pattern check functions //

    public static final Predicate<String> isImportStatement = (line) -> line.startsWith("import");
    public static final Predicate<String> isOptionDeclaration = (line) -> line.startsWith("option ");
    public static final Predicate<String> isPackageDeclaration = (line) -> line.startsWith("package ");
    public static final Predicate<String> isSyntaxDeclaration = (line) -> line.startsWith("syntax");

    // Comment and blank line pattern check functions //

    public static final Predicate<String> isBlankLine = String::isBlank;
    public static final Predicate<String> isLineComment = (line) -> line.startsWith(LINE_COMMENT_SLASHES);
    public static final Predicate<String> isBlockCommentOpener = (line) -> line.startsWith("/*");
    public static final Predicate<String> isBlockCommentCloser = (line) -> line.endsWith("*/");
    public static final Predicate<String> isComment = (line) -> isLineComment.test(line) || isBlockCommentOpener.test(line);
    public static final Predicate<String> hasAppendedLineComment = (line) -> !isLineComment.test(line) && line.contains(LINE_COMMENT_SLASHES);

    // Comment extraction functions //

    @SuppressWarnings("IndexOfReplaceableByContains")
    public static final Function<String, String> toCleanSingleLineComment = (comment) ->
            comment.indexOf("// ") >= 0
                    ? comment.replaceFirst("// ", "").trim()
                    : comment.substring(LINE_COMMENT_SLASHES.length()).trim();


    public static final Function<String, String> toCleanLineComment = (comment) -> {
        if (comment.contains("///"))
            return comment; // Don't bother trying to clean up comments like this:  /////////////

        Predicate<String> isMultiLineComment = (c) -> c.split("\n").length > 1;
        if (isMultiLineComment.test(comment)) {
            StringBuilder cleanBuilder = new StringBuilder();
            String[] lines = comment.split("\n");
            for (String line : lines)
                cleanBuilder.append(toCleanSingleLineComment.apply(line)).append("\n");

            return cleanBuilder.toString();
        } else {
            return toCleanSingleLineComment.apply(comment);
        }
    };

    public static final Function<String, String> toCleanBlockComment = (comment) ->
            comment.replace("/*", "")
                    .replace("*/", "")
                    .replace("*", "")
                    .trim();

    public static final BiFunction<String, Optional<TextBlock>, String> removeAppendedLineComment = (line, previousTextBlock) -> {
        if (previousTextBlock.isEmpty()) {
            return line;
        } else {
            var commentBlock = previousTextBlock.get();
            if (commentBlock.getTextBlockType().equals(LINE_COMMENT)) {
                String comment = commentBlock.getText();
                return line.substring(0, line.indexOf(comment));
            } else {
                throw new IllegalArgumentException(format("%s is not a LINE_COMMENT.", commentBlock));
            }
        }
    };

    public static Optional<TextBlock> getAppendedLineCommentBlock(String line) {
        // Appended block comments will not be supported.
        if (hasAppendedLineComment.test(line)) {
            int appendedCommentIdx = line.indexOf(LINE_COMMENT_SLASHES);
            return Optional.of(new TextBlock(line.substring(appendedCommentIdx), LINE_COMMENT));
        } else {
            return Optional.empty();
        }
    }

    // Protobuf declaration open and close ('{' and '}') pattern check functions.                       //
    // All declarations are enclosed in curly braces, and nothing else can be, exception comment text.  //

    public static final Predicate<String> isDeclaringProtobufDefinition = (line) -> line.endsWith(OPENING_CURLY_BRACE);
    public static final Predicate<String> isClosingProtobufDefinition = (line) -> line.startsWith(CLOSING_CURLY_BRACE);

    // Deprecated field pattern check functions //

    public static final Predicate<String> hasDeprecationOption = (line) -> line.contains(DEPRECATION_OPTION);
    public static final Predicate<String> isDeprecated = (line) -> line.contains(DEPRECATION_OPTION);

    // Enum pattern check functions //

    public static final Predicate<String> isEnumDeclaration = (line) -> line.startsWith("enum ") && line.endsWith(OPENING_CURLY_BRACE);
    public static final Predicate<String> isEnumConstantDeclaration = (line) -> {
        try {
            // If the line can be parsed without an exception, it is an enum constant declaration.
            boolean isDeprecatedConstant = isDeprecated.test(line);
            String[] parts = getEnumConstantParts(line, isDeprecatedConstant);
            getEnumConstantOrdinal(parts, isDeprecatedConstant);
            return true;
        } catch (Exception e) {
            return false;
        }
    };

    // Message pattern check functions //

    public static final Predicate<String> isMessageFieldDeclaration = (line) ->
            !isEnumDeclaration.test(line)
                    && line.split(" ").length >= 3
                    && hasPattern(line, MESSAGE_FIELD_PATTERN);
    public static final Predicate<String> isMessageMapFieldDeclaration = (line) -> line.startsWith("map") && hasPattern(line, MAP_KV_TYPES_PATTERN);
    public static final Predicate<String> isOneOfMessageFieldDeclaration = (line) -> line.startsWith(ONEOF_MESSAGE) && line.endsWith(OPENING_CURLY_BRACE);
    public static final Predicate<String> isMessageDeclaration = (line) -> line.startsWith("message ") && line.endsWith(OPENING_CURLY_BRACE);
    public static final Predicate<String> isReservedFieldDeclaration = (line) -> line.startsWith("reserved ");

    // Returns true if a protobuf field type is one of the standard protobuffer scalar types
    // as defined in https://developers.google.com/protocol-buffers/docs/proto3#scalar.
    public static final Predicate<String> isScalarDataType = (dataType) ->
            stream(ProtobufDataType.values()).anyMatch(t -> t.formalName.equals(dataType));

    // gRPC service and rpc method pattern check functions //

    public static final Predicate<String> isGrpcServiceDeclaration = (line) -> line.startsWith("service ") && line.endsWith(OPENING_CURLY_BRACE);
    public static final Predicate<String> isRpcMethodDeclaration = (line) -> line.startsWith("rpc ") && line.endsWith(OPENING_CURLY_BRACE);


    // Pattern matching utils //

    public static boolean hasPattern(String string, Pattern pattern) {
        return pattern.matcher(string).find();
    }

    public static Optional<String> findFirstMatch(String string, Pattern pattern) {
        Matcher m = pattern.matcher(string);
        if (m.find()) {
            return Optional.of(m.group(1));
        } else {
            return Optional.empty();
        }
    }

    // String manipulation utils //

    public static String indentedText(String trimmedText, int baseLevel) {
        if (baseLevel == 0)
            return trimmedText + "\n";

        char[] baseIndentation = new char[baseLevel];
        Arrays.fill(baseIndentation, INDENT_CHAR);
        String[] lines = trimmedText.split("\n");
        StringBuilder stringBuilder = new StringBuilder();
        Arrays.stream(lines).forEach(l -> {
            stringBuilder.append(new String(baseIndentation));
            stringBuilder.append(l);
            stringBuilder.append("\n");
        });
        return stringBuilder.toString();
    }

    public static String stripSemicolon(String line) {
        int semicolonIndex = line.indexOf(";");
        return semicolonIndex >= 0
                ? line.substring(0, semicolonIndex)
                : line;
    }

    public static String stripDeprecatedOption(String line) {
        int optionIndex = line.indexOf(DEPRECATION_OPTION);
        // Note this will also remove a trailing semicolon if it exists.
        return optionIndex >= 0
                ? line.substring(0, optionIndex)
                : line;
    }

    public static final Function<Path, String> toText = (filePath) -> {
        try {
            byte[] bytes = Files.readAllBytes(filePath);
            return new String(bytes, UTF_8);
        } catch (IOException ex) {
            throw new IllegalArgumentException(format("Could not convert content of file %s to text.", filePath));
        }
    };

    // Protobuf definition text parsing and manipulation utils //

    public static String[] getEnumConstantParts(String line, boolean isDeprecated) {
        return isDeprecated ? line.split(" ") : line.split("=");
    }

    public static int getEnumConstantOrdinal(String[] parts, boolean isDeprecated) {
        return isDeprecated
                ? Integer.parseInt(parts[2].trim())
                : Integer.parseInt(stripSemicolon(parts[1].trim()));
    }

    public static String[] getMapKeyValueTypes(String line) {
        try {
            Optional<String> keyValueDataTypes = findFirstMatch(line, MAP_KV_TYPES_PATTERN);
            if (keyValueDataTypes.isPresent()) {
                String[] kvTypes = keyValueDataTypes.get().split(",");
                String keyType = kvTypes[0].trim();
                String valueType = kvTypes[1].trim();
                return new String[]{keyType, valueType};
            } else {
                throw new IllegalStateException(format("Did not find map field <key,value> type declaration in line:  %s", line));
            }
        } catch (Exception ex) {
            throw new IllegalStateException(format("Could not parse msg map field kv data types for line:  %s", line));
        }
    }

    public static String[] getMapFieldNameAndFieldNumberParts(String line, boolean isDeprecated) {
        String rawNameAndNumber = line.substring(line.indexOf(">") + 1);
        return isDeprecated
                ? stripDeprecatedOption(rawNameAndNumber).split("=")
                : stripSemicolon(rawNameAndNumber).split("=");
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public static String getCleanBlockText(String line, Optional<TextBlock> appendedLineCommentTextBlock) {
        // Returns the given line without an appended comment, if present.
        return appendedLineCommentTextBlock.isPresent()
                ? removeAppendedLineComment.apply(line, appendedLineCommentTextBlock)
                : line;
    }
}
