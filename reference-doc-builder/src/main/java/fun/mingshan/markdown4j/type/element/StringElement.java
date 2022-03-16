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

import java.util.Objects;

/**
 * @author hanjuntao
 * @date 2022/1/18
 */
@SuppressWarnings("ALL")
public class StringElement implements Element {

    private final String content;

    public StringElement(String content) {
        this.content = content;
    }

    public String getContent() {
        return content;
    }

    @Override
    public String toMd() {
        return this.content;
    }

    @Override
    public ElementType getType() {
        return ElementType.STRING;
    }

    public static StringElementBuilder builder() {
        return new StringElementBuilder();
    }

    public static class StringElementBuilder {
        private String content;

        StringElementBuilder() {
        }

        public StringElementBuilder content(String content) {
            Objects.requireNonNull(content, "content cannot be null");
            this.content = content;
            return this;
        }

        public StringElement build() {
            return new StringElement(this.content);
        }
    }
}
