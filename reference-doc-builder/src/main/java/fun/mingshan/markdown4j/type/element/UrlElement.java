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
 * <pre>
 *     [普通链接](http://localhost/)
 * </pre>
 *
 * @author hanjuntao
 * @date 2022/1/17
 */
@SuppressWarnings({"JavaDoc", "ClassCanBeRecord"})
public class UrlElement implements Element {

    private final String tips;
    private final String url;

    public UrlElement(String tips, String url) {
        this.tips = tips;
        this.url = url;
    }

    public String getTips() {
        return tips;
    }

    public String getUrl() {
        return url;
    }

    @Override
    public String toMd() {
        ElementEncoder encoder = ElementEncoderFactory.getEncoder(ElementType.URL);
        return encoder.encode(this);
    }

    @Override
    public ElementType getType() {
        return ElementType.URL;
    }

    public static UrlElementBuilder builder() {
        return new UrlElementBuilder();
    }

    public static class UrlElementBuilder {
        private String tips;
        private String url;

        UrlElementBuilder() {
        }

        public UrlElementBuilder tips(String tips) {
            Objects.requireNonNull(tips, "tips cannot be null");
            this.tips = tips;
            return this;
        }

        public UrlElementBuilder url(String url) {
            Objects.requireNonNull(url, "url cannot be null");
            this.url = url;
            return this;
        }

        public UrlElement build() {
            return new UrlElement(this.tips, this.url);
        }
    }
}
