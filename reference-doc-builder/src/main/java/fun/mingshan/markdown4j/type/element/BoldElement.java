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
 * 加粗
 *
 * @author hanjuntao
 * @date 2022/1/18
 */
@SuppressWarnings("ALL")
public class BoldElement implements Element {
    private final String content;

    public BoldElement(String content) {
        this.content = content;
    }

    public String getContent() {
        return content;
    }

    @Override
    public String toMd() {
        ElementEncoder encoder = ElementEncoderFactory.getEncoder(ElementType.BOLD);
        return encoder.encode(this);
    }

    @Override
    public ElementType getType() {
        return ElementType.BOLD;
    }

    public static BoldElementBuilder builder() {
        return new BoldElementBuilder();
    }

    public static class BoldElementBuilder {
        private String content;

        BoldElementBuilder() {
        }

        public BoldElementBuilder content(String content) {
            Objects.requireNonNull(content, "content cannot be null");
            this.content = content;
            return this;
        }

        public BoldElement build() {
            return new BoldElement(this.content);
        }
    }
}
