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
package fun.mingshan.markdown4j.writer;

import fun.mingshan.markdown4j.Markdown;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author hanjuntao
 * @date 2022/1/18
 */
@SuppressWarnings("ALL")
public class MdWriter {

    public static void write(Markdown markdown) throws IOException {
        write(Paths.get("./"), markdown);
    }

    public static void write(Path dir, Markdown markdown) throws IOException {
        String fileName = dir.resolve(markdown.getName() + ".md").toString();
        PrintWriter printWriter = new PrintWriter(fileName, UTF_8);
        byte[] bytes = markdown.toString().getBytes(UTF_8);
        String unicodeContent = new String(bytes, UTF_8);
        printWriter.write(unicodeContent);
        printWriter.flush();
        printWriter.close();
    }
}
