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
 * 引用块
 *
 * <pre>
 *     > aadadad
 * </pre>
 *
 * @author hanjuntao
 * @date 2022/1/17
 */
public class ReferenceBlock implements Block {

    private final String content;

    public ReferenceBlock(String content) {
        this.content = content;
    }

    public String getContent() {
        return content;
    }

    @Override
    public BlockType getType() {
        return BlockType.REFERENCE;
    }

    @Override
    public String toMd() {
        BlockEncoder encoder = BlockEncoderFactory.getEncoder(BlockType.REFERENCE);
        return encoder.encode(this);
    }


    public static ReferenceBlockBuilder builder() {
        return new ReferenceBlockBuilder();
    }

    public static class ReferenceBlockBuilder {
        private String content;

        ReferenceBlockBuilder() {
        }

        public ReferenceBlockBuilder content(String content) {
            Objects.requireNonNull(content, "content cannot be null");
            this.content = content;
            return this;
        }

        public ReferenceBlock build() {
            return new ReferenceBlock(this.content);
        }
    }
}
