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

import java.util.Objects;

/**
 * @author hanjuntao
 * @date 2022/1/18
 */
public class StringBlock implements Block {

    private final String content;

    public StringBlock(String content) {
        this.content = content;
    }

    public String getContent() {
        return this.content;
    }

    @Override
    public String toMd() {
        return content;
    }

    @Override
    public BlockType getType() {
        return BlockType.STRING;
    }

    public static StringBlockBuilder builder() {
        return new StringBlockBuilder();
    }

    public static class StringBlockBuilder {
        private String content;

        StringBlockBuilder() {
        }

        public StringBlockBuilder content(String content) {
            Objects.requireNonNull(content, "content cannot be null");
            this.content = content;
            return this;
        }

        public StringBlock build() {
            return new StringBlock(this.content);
        }
    }
}
