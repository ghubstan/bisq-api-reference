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

import java.util.Objects;

/**
 * 标题块
 *
 * <pre>
 *     # 一级标题
 *     ## 二级标题
 *     ### 三级标题
 *     #### 四级标题
 *     #####  五级标题
 * </pre>
 *
 * @author hanjuntao
 * @date 2022/1/17
 */
public class TitleBlock implements Block {

    private final Level level;
    private final String content;

    public TitleBlock(Level level, String content) {
        this.level = level;
        this.content = content;
    }

    public Level getLevel() {
        return level;
    }

    public String getContent() {
        return content;
    }

    @Override
    public BlockType getType() {
        return BlockType.TITLE;
    }

    @Override
    public String toMd() {
        BlockEncoder encoder = BlockEncoderFactory.getEncoder(BlockType.TITLE);
        return encoder.encode(this);
    }

    /**
     * 标题级别枚举
     */
    public enum Level {
        FIRST,
        SECOND,
        THIRD,
        FOURTH,
        FIFTH
    }

    public static TitleBlockBuilder builder() {
        return new TitleBlockBuilder();
    }

    public static class TitleBlockBuilder {
        private Level level;
        private String content;

        TitleBlockBuilder() {
        }

        public TitleBlockBuilder level(Level level) {
            Objects.requireNonNull(level, "level cannot be null");
            this.level = level;
            return this;
        }

        public TitleBlockBuilder content(String content) {
            Objects.requireNonNull(content, "content cannot be null");
            this.content = content;
            return this;
        }

        public TitleBlock build() {
            return new TitleBlock(this.level, this.content);
        }
    }
}
