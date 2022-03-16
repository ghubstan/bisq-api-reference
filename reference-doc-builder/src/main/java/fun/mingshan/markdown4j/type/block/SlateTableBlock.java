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
package fun.mingshan.markdown4j.type.block;

import fun.mingshan.markdown4j.encoder.block.BlockEncoder;
import fun.mingshan.markdown4j.encoder.block.BlockEncoderFactory;

import java.util.List;
import java.util.Objects;

/**
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
 */
public class SlateTableBlock implements Block {

    private final List<String> titles;
    private final List<TableRow> rows;

    public SlateTableBlock(List<String> titles, List<TableRow> rows) {
        this.titles = titles;
        this.rows = rows;
    }

    public List<String> getTitles() {
        return titles;
    }

    public List<TableRow> getRows() {
        return rows;
    }

    @Override
    public BlockType getType() {
        return BlockType.SLATE_TABLE;
    }

    @Override
    public String toMd() {
        BlockEncoder encoder = BlockEncoderFactory.getEncoder(BlockType.SLATE_TABLE);
        return encoder.encode(this);
    }

    // @Data
    public static class TableRow {
        private List<String> columns;

        public TableRow() {
        }

        public TableRow(List<String> columns) {
            this.columns = columns;
        }

        public List<String> getColumns() {
            return columns;
        }

        public void setColumns(List<String> columns) {
            this.columns = columns;
        }
    }


    public static SlateTableBlockBuilder builder() {
        return new SlateTableBlockBuilder();
    }

    public static class SlateTableBlockBuilder {
        private List<String> titles;
        private List<TableRow> rows;

        SlateTableBlockBuilder() {
        }

        public SlateTableBlockBuilder titles(List<String> titles) {
            Objects.requireNonNull(titles, "titles cannot be null");
            this.titles = titles;
            return this;
        }

        public SlateTableBlockBuilder rows(List<TableRow> rows) {
            Objects.requireNonNull(titles, "rows cannot be null");
            this.rows = rows;
            return this;
        }

        public SlateTableBlock build() {
            return new SlateTableBlock(this.titles, this.rows);
        }
    }
}
