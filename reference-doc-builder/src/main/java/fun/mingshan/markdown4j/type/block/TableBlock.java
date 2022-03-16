/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fun.mingshan.markdown4j.type.block;

import fun.mingshan.markdown4j.encoder.block.BlockEncoder;
import fun.mingshan.markdown4j.encoder.block.BlockEncoderFactory;

import java.util.List;
import java.util.Objects;

/**
 * 表格块
 *
 * <pre>
 * | header1 | header2 |
 * | ------- | ------- |
 * |   wqeq  |  qwq    |
 * </pre>
 *
 * @author hanjuntao
 * @date 2022/1/17
 */
public class TableBlock implements Block {

    private final List<String> titles;
    private final List<TableRow> rows;

    public TableBlock(List<String> titles, List<TableRow> rows) {
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
        return BlockType.TABLE;
    }

    @Override
    public String toMd() {
        BlockEncoder encoder = BlockEncoderFactory.getEncoder(BlockType.TABLE);
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


    public static TableBlockBuilder builder() {
        return new TableBlockBuilder();
    }

    public static class TableBlockBuilder {
        private List<String> titles;
        private List<TableRow> rows;

        TableBlockBuilder() {
        }

        public TableBlockBuilder titles(List<String> titles) {
            Objects.requireNonNull(titles, "titles cannot be null");
            this.titles = titles;
            return this;
        }

        public TableBlockBuilder rows(List<TableRow> rows) {
            Objects.requireNonNull(titles, "rows cannot be null");
            this.rows = rows;
            return this;
        }

        public TableBlock build() {
            return new TableBlock(this.titles, this.rows);
        }
    }
}
