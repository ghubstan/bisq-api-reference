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
package fun.mingshan.markdown4j.type.element;

import fun.mingshan.markdown4j.encoder.element.ElementEncoder;
import fun.mingshan.markdown4j.encoder.element.ElementEncoderFactory;

import java.util.Objects;

/**
 * 斜体
 *
 * <pre>
 *     _斜体字_
 * </pre>
 *
 * @author hanjuntao
 * @date 2022/1/18
 */
@SuppressWarnings({"JavaDoc", "ClassCanBeRecord"})
public class ItalicElement implements Element {
    private final String content;

    public ItalicElement(String content) {
        this.content = content;
    }

    public String getContent() {
        return content;
    }

    @Override
    public String toMd() {
        ElementEncoder encoder = ElementEncoderFactory.getEncoder(ElementType.ITALIC);
        return encoder.encode(this);
    }

    @Override
    public ElementType getType() {
        return ElementType.ITALIC;
    }


    public static ItalicElementBuilder builder() {
        return new ItalicElementBuilder();
    }

    public static class ItalicElementBuilder {
        private String content;

        ItalicElementBuilder() {
        }

        public ItalicElementBuilder content(String content) {
            Objects.requireNonNull(content, "content cannot be null");
            this.content = content;
            return this;
        }

        public ItalicElement build() {
            return new ItalicElement(this.content);
        }
    }
}
