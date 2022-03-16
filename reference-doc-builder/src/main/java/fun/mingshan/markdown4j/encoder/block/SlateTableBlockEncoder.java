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
package fun.mingshan.markdown4j.encoder.block;

import fun.mingshan.markdown4j.type.block.Block;
import fun.mingshan.markdown4j.type.block.BlockType;
import fun.mingshan.markdown4j.type.block.SlateTableBlock;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import static fun.mingshan.markdown4j.constant.FlagConstants.*;
import static fun.mingshan.markdown4j.type.block.BlockType.SLATE_TABLE;

/**
 * Encode Slate compatible table block.
 * <p>
 * Slate Tables use PHP Markdown Extra style tables:
 * <p>
 * <pre>
 * Table Header 1 | Table Header 2 | Table Header 3
 * -------------- | -------------- | --------------
 * Row 1 col 1 | Row 1 col 2 | Row 1 col 3
 * Row 2 col 1 | Row 2 col 2 | Row 2 col 3
 * </pre>
 * Note that the pipes do not need to line up with each other on each line.
 * <p>
 * Note that Markdown does not support column spanning (nested, ugly html does).
 */
public class SlateTableBlockEncoder implements BlockEncoder {

    //
    // Slate md table blocks must look line this:
    //
    // Parameter | Default | Description
    // --------- | ------- | -----------
    // include_cats | false | If set to true, the result will also include cats.
    // available | true | If set to false, the result will include kittens that have already been adopted.
    //
    // Not like this:
    // | header1 | header2 |
    // | ------- | ------- |
    // |   wqeq  |  qwq    |
    //
    // The differences are:
    //  The omission of '|' SEP chars at the beginning and end of each line in hanjuntao's TableBlockEncoder,
    //  The substitution of newline (\n) characters for html line break tags (</br>).
    //  Multi-line column values need to be transformed:  replace '\n' with '</br>'.

    private static final String SEP = "|";  // Do not prepend and end each line with SEP.
    private static final String SPE2 = "-------------";

    private final Predicate<String> isMultilineColumnValue = (c) -> c.split(NEW_LINE_BREAK).length > 1;

    @Override
    public String encode(Block block) {
        SlateTableBlock tableBlock = (SlateTableBlock) block;
        List<String> titles = tableBlock.getTitles();
        StringBuilder result = new StringBuilder();   // Do not prepend the titles line with a SEP.

        // Write the titles.
        for (int i = 0; i < titles.size(); i++) {
            String title = titles.get(i);
            result.append(SPACE).append(title).append(SPACE);
            // Do not end each line with SEP.
            if (i + 1 < titles.size())
                result.append(SEP);
        }
        result.append(NEW_LINE_BREAK);  // Do I really want this </br> tag in the markdown?

        // Write the 2nd line, i.e., "--------- | ------- | -----------".
        for (int i = 0; i < titles.size(); i++) {
            result.append(SPACE).append(SPE2).append(SPACE);
            // Do not end each line with SEP.
            if (i + 1 < titles.size())
                result.append(SEP);
        }
        result.append(NEW_LINE_BREAK);

        // Write the rows.
        List<SlateTableBlock.TableRow> rows = tableBlock.getRows();
        for (SlateTableBlock.TableRow tableRow : rows) {
            List<String> columns = tableRow.getColumns();
            for (int i = 0; i < columns.size(); i++) {
                String column = columns.get(i);

                // Multi-line column values separated by newline (\n) chars will mess up md table rendering.
                // Replace newlines with html line break chars: (</br>.
                if (isMultilineColumnValue.test(column))
                    column = column.replaceAll(NEW_LINE_BREAK, HTML_LINE_BREAK);

                result.append(SPACE);
                result.append(Objects.requireNonNullElse(column, SPACE));
                result.append(SPACE);
                if (i + 1 < columns.size())
                    result.append(SEP);
            }
            result.append(NEW_LINE_BREAK);
        }
        result.append(NEW_LINE_BREAK);
        return result.toString();
    }

    @Override
    public BlockType getType() {
        return SLATE_TABLE;
    }
}
