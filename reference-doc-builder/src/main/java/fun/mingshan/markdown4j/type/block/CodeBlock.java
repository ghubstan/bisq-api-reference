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
 * 代码块
 *
 * <pre>
 *     ```java
 *     public class CodeBlock extends Block {
 *     }
 *     ```
 * </pre>
 *
 * @author hanjuntao
 * @date 2022/1/17
 */
@SuppressWarnings("ALL")
public class CodeBlock implements Block {

    private final String language;
    private final String content;

    public CodeBlock(String language, String content) {
        this.language = language;
        this.content = content;
    }

    public String getLanguage() {
        return language;
    }

    public String getContent() {
        return content;
    }

    @Override
    public BlockType getType() {
        return BlockType.CODE;
    }

    @Override
    public String toMd() {
        BlockEncoder encoder = BlockEncoderFactory.getEncoder(BlockType.CODE);
        return encoder.encode(this);
    }

    /**
     * 语言
     *
     * @author hanjuntao
     * @date 2022/1/17
     */
    public enum Language {
        JAVA("Java"),
        C("C"),
        CPLUSPLUS("C++"),
        JAVASCRIPT("Javascript"),
        PYTHON("Python"),
        CLI("Bisq CLI");

        private final String description;

        Language(String description) {
            this.description = description;
        }

        public String description() {
            return description;
        }
    }


    public static CodeBlockBuilder builder() {
        return new CodeBlockBuilder();
    }

    public static class CodeBlockBuilder {
        private String language;
        private String content;

        CodeBlockBuilder() {
        }


        public CodeBlockBuilder language(String language) {
            Objects.requireNonNull(language, "language cannot be null");
            this.language = language;
            return this;
        }

        public CodeBlockBuilder content(String content) {
            Objects.requireNonNull(content, "content cannot be null");
            this.content = content;
            return this;
        }

        public CodeBlock build() {
            return new CodeBlock(this.language, this.content);
        }
    }
}
