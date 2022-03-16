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

import bisq.apidoc.protobuf.definition.FieldDefinition;
import bisq.apidoc.protobuf.definition.MapFieldDefinition;
import bisq.apidoc.protobuf.definition.MessageDefinition;
import fun.mingshan.markdown4j.type.block.Block;
import fun.mingshan.markdown4j.type.block.SlateTableBlock;
import fun.mingshan.markdown4j.type.block.StringBlock;
import fun.mingshan.markdown4j.type.element.UrlElement;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static bisq.apidoc.protobuf.ProtoParserUtil.isScalarDataType;
import static java.util.Arrays.asList;
import static java.util.Comparator.comparing;

@Slf4j
public class FieldTableBlockBuilder {

    private static final String BEGIN_ONE_OF_MESSAGE = "one of {";
    private static final String END_ONE_OF_MESSAGE = "}";
    private static final String DESCRIPTION = "Description";
    private static final String NAME = "Name";
    private static final String TYPE = "Type";

    // Used for indentation in Markdown document.
    private static final String NON_BREAKING_SPACE = "&nbsp;";
    private static final String FOUR_NBSP = new String(new char[4]).replace("\0", NON_BREAKING_SPACE);

    private final Supplier<SlateTableBlock.TableRow> anotherRow = SlateTableBlock.TableRow::new;
    private final Predicate<String> shouldWriteInternalLink = (type) -> type != null && !isScalarDataType.test(type);
    private final Function<FieldDefinition, String> toDecoratedFieldType = (f) ->
            f.isRepeated() ? "array " + f.type() : f.type();

    private final MessageDefinition messageDefinition;
    private final boolean failOnMissingDocumentation;
    private final List<SlateTableBlock.TableRow> rows;

    public FieldTableBlockBuilder(MessageDefinition messageDefinition, boolean failOnMissingDocumentation) {
        this.messageDefinition = messageDefinition;
        this.failOnMissingDocumentation = failOnMissingDocumentation;
        this.rows = new ArrayList<>();
    }

    public Block build() {
        if (messageDefinition.fields().size() == 0)
            throw new IllegalArgumentException("Cannot build Field md table block from empty field map.");

        List<FieldDefinition> fields = messageDefinition.fields().values().stream().toList();
        fields.forEach(f -> {
            if (f.isMapField()) {
                SlateTableBlock.TableRow row = anotherRow.get();
                List<String> mapFieldColumns = getMapFieldColumns((MapFieldDefinition) f);
                row.setColumns(mapFieldColumns);
                rows.add(row);
            } else if (f.isOneOfMessageField()) {
                SlateTableBlock.TableRow startRow = anotherRow.get();
                startRow.setColumns(asList(BEGIN_ONE_OF_MESSAGE,
                        NON_BREAKING_SPACE,
                        "Field value will be one of the following."));
                rows.add(startRow);

                // TODO Append one of field rows, each name is indented by four nbsp; chars.
                List<List<String>> oneOfFieldColumns = getOneOfFieldColumns(f.oneOfFieldChoices());
                oneOfFieldColumns.forEach(row -> {
                    SlateTableBlock.TableRow choiceRow = anotherRow.get();
                    choiceRow.setColumns(row);
                    rows.add(choiceRow);
                });

                SlateTableBlock.TableRow endRow = anotherRow.get();
                endRow.setColumns(asList(END_ONE_OF_MESSAGE, NON_BREAKING_SPACE, NON_BREAKING_SPACE));
                rows.add(endRow);
            } else {
                // If the field type is a global enum or message, generate an internal link to it
                // and use that in the table row's 'Type' column.  But do not generate a link if the
                // field type is a (local) enum declared within the message definition because you
                // cannot navigate to it.  In Slate, only level 1 and 2 headers will appear in the
                // table of contents.
                boolean isLocalEnum = messageDefinition.enums().containsKey(f.type());
                Optional<StringBlock> optionalInternalLink = isLocalEnum
                        ? Optional.empty()
                        : getInternalLink(f);
                String type = optionalInternalLink.isPresent()
                        ? optionalInternalLink.get().toMd()
                        : toDecoratedFieldType.apply(f);
                SlateTableBlock.TableRow row = anotherRow.get();
                row.setColumns(asList(f.name(),
                        type,
                        f.description()));
                rows.add(row);
            }
        });
        SlateTableBlock tableBlock = SlateTableBlock.builder()
                .titles(asList(NAME, TYPE, DESCRIPTION))
                .rows(rows)
                .build();
        Map<String, String> templateVars = new HashMap<>() {{
            put("field.tbl", tableBlock.toMd());
        }};
        Template template = new Template("message-fields.md", templateVars, failOnMissingDocumentation);
        return StringBlock.builder().content(template.getContent()).build();
    }

    private List<String> getMapFieldColumns(MapFieldDefinition mapField) {
        String mapValueType = mapField.valueType();
        if (isScalarDataType.test(mapValueType)) {
            // LT and GT symbols won't work in MD by simply escaping them;  use the decimal unicode point.
            String mapSpec = "map" + "&#60;" + mapField.keyType() + ", " + mapValueType + "&#62;";
            return asList(mapField.name(),
                    mapSpec,
                    mapField.description());
        } else {
            throw new UnsupportedOperationException("TODO generate internal link for custom map value type: " + mapValueType);
        }
    }

    /**
     * Returns a list of row columns for each oneof message field choice.
     */
    private List<List<String>> getOneOfFieldColumns(List<FieldDefinition> choices) {
        // Sort the choice byte type, ignoring field numbers.  Should I?
        choices.sort(comparing(FieldDefinition::type));
        List<List<String>> rows = new ArrayList<>();
        choices.forEach(f -> {
            // One Of Fields cannot be repeated, or map fields;  do
            // not need to decorate any type column values with "array".
            Optional<StringBlock> optionalInternalLink = getInternalLink(f);
            String type = optionalInternalLink.isPresent()
                    ? optionalInternalLink.get().toMd()
                    : f.type();
            rows.add(asList(FOUR_NBSP + f.name(), type, f.description()));
        });
        return rows;
    }

    /**
     * Returns an optional Markdown block for an internal link, if the
     * field type refers to a custom protobuf type, else Optional.empty().
     */
    private Optional<StringBlock> getInternalLink(FieldDefinition fieldDefinition) {
        // A repeated field requires an "array" prefix on the type column value.
        String decoratedType = toDecoratedFieldType.apply(fieldDefinition);
        return shouldWriteInternalLink.test(fieldDefinition.type())
                ? Optional.of(UrlElement.builder()
                .tips(decoratedType)
                .url("#" + fieldDefinition.type().toLowerCase())
                .build()
                .toBlock())
                : Optional.empty();

    }

    /**
     * Returns an optional Markdown block for an internal link, if the
     * field type refers to a custom protobuf type, else Optional.empty().
     */
    @Deprecated
    private Optional<StringBlock> getInternalLinkDeprecated(String fieldType) {
        return shouldWriteInternalLink.test(fieldType)
                ? Optional.of(UrlElement.builder()
                .tips(fieldType)
                .url("#" + fieldType.toLowerCase())
                .build()
                .toBlock())
                : Optional.empty();

    }
}
