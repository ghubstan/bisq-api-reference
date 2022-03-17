/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */
package bisq.apidoc.markdown;

import bisq.apidoc.protobuf.definition.RpcMethodDefinition;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static bisq.apidoc.protobuf.ProtoParserUtil.toText;

/**
 * Generates the slate Markdown String from the example source code
 * files (shell, java and python), for a given rpc method definition.
 * <p>
 * The content must be inserted into the encapsulating rpc method block's
 * second line, <i>after</i> its variable substitution has been performed.
 */
@Slf4j
public class CodeExamples {

    // TODO  Is hard-coding paths to example source files OK?
    private static final String CLI_EXAMPLES_DIR = "cli-examples";
    private static final String JAVA_EXAMPLES_DIR = "java-examples/src/main/java/bisq/rpccalls";
    private static final String PYTHON_EXAMPLES_DIR = "python-examples/python_examples/bisq/rpccalls";

    private static final String START_SHELL_MD = "```shell";
    private static final String START_JAVA_MD = "```java";
    private static final String START_PYTHON_MD = "```python";
    private static final String END_SAMPLE_MD = "```";

    private static final String SHELLSCRIPT_EXTENSION = ".sh";
    private static final String JAVA_EXTENSION = ".java";
    private static final String PYTHON_EXTENSION = ".py";

    private final BiFunction<RpcMethodDefinition, String, String> toCliSourceFileName = (method, extension) -> {
        if (!extension.equals(SHELLSCRIPT_EXTENSION))
            throw new IllegalArgumentException("Invalid CLI code example file extension: " + extension);
        else
            return CLI_EXAMPLES_DIR + File.separatorChar + method.name() + extension;
    };

    private final BiFunction<RpcMethodDefinition, String, String> toJavaSourceFileName = (method, extension) -> {
        if (!extension.equals(JAVA_EXTENSION))
            throw new IllegalArgumentException("Invalid Java code example file extension: " + extension);
        else
            return JAVA_EXAMPLES_DIR + File.separatorChar + method.name() + extension;
    };

    private final BiFunction<RpcMethodDefinition, String, String> toPythonSourceFileName = (method, extension) -> {
        if (!extension.equals(PYTHON_EXTENSION)) {
            throw new IllegalArgumentException("Invalid Python code example file extension: " + extension);
        } else {
            String firstLetter = method.name().substring(0, 1).toLowerCase();
            String idiomaticSourceFileSuffix = firstLetter + method.name().substring(1)
                    .replaceAll("([A-Z])", "_$1")
                    .toLowerCase();
            return PYTHON_EXAMPLES_DIR + File.separatorChar + idiomaticSourceFileSuffix + extension;
        }
    };

    private final BiFunction<RpcMethodDefinition, String, File> toSourceFile = (method, extension) -> {
        if (extension.equals(SHELLSCRIPT_EXTENSION))
            return new File(toCliSourceFileName.apply(method, extension));
        else if (extension.equals(JAVA_EXTENSION))
            return new File(toJavaSourceFileName.apply(method, extension));
        else if (extension.equals(PYTHON_EXTENSION))
            return new File(toPythonSourceFileName.apply(method, extension));
        else
            throw new IllegalArgumentException("Invalid code example file extension: " + extension);
    };

    private final Predicate<RpcMethodDefinition> hasCliExample = (method) -> toSourceFile.apply(method, SHELLSCRIPT_EXTENSION).exists();
    private final Predicate<RpcMethodDefinition> hasJavaExample = (method) -> toSourceFile.apply(method, JAVA_EXTENSION).exists();
    private final Predicate<RpcMethodDefinition> hasPythonExample = (method) -> toSourceFile.apply(method, PYTHON_EXTENSION).exists();

    private final RpcMethodDefinition rpcMethodDefinition;
    private static String javaBoilerplateSource;

    public CodeExamples(RpcMethodDefinition rpcMethodDefinition) {
        this.rpcMethodDefinition = rpcMethodDefinition;
    }

    public String getContent() {
        StringBuilder examplesBuilder = new StringBuilder();

        examplesBuilder.append(START_SHELL_MD).append("\n");
        if (hasCliExample.test(rpcMethodDefinition)) {
            String rawSource = getRawContent(toSourceFile.apply(rpcMethodDefinition, SHELLSCRIPT_EXTENSION));
            rawSource.lines().collect(Collectors.toList()).forEach(l -> {
                if (!l.startsWith("source ")) {
                    examplesBuilder.append(l.replace("$BISQ_HOME", "."));
                    examplesBuilder.append("\n");
                }
            });
            examplesBuilder.append("\n");
        }
        examplesBuilder.append(END_SAMPLE_MD).append("\n").append("\n");

        examplesBuilder.append(START_JAVA_MD).append("\n");
        if (hasJavaExample.test(rpcMethodDefinition)) {
            File sourceFile = toSourceFile.apply(rpcMethodDefinition, JAVA_EXTENSION);
            String displayedSource = getRawContent(sourceFile).trim();
            examplesBuilder.append(displayedSource);
            examplesBuilder.append("\n");
            if (displayedSource.lines().collect(Collectors.toList()).size() > 1) {
                examplesBuilder.append("\n");
                examplesBuilder.append("//////////////////").append("\n");
                examplesBuilder.append("// BaseJavaExample").append("\n");
                examplesBuilder.append("//////////////////").append("\n");
                examplesBuilder.append("\n");
                examplesBuilder.append(getJavaBoilerplateSource());
                examplesBuilder.append("\n");
            }
        }
        examplesBuilder.append(END_SAMPLE_MD).append("\n");

        examplesBuilder.append(START_PYTHON_MD).append("\n");
        if (hasPythonExample.test(rpcMethodDefinition)) {
            String pythonSource = getRawContent(toSourceFile.apply(rpcMethodDefinition, PYTHON_EXTENSION));
            examplesBuilder.append(pythonSource.trim());
            examplesBuilder.append("\n");
        }
        examplesBuilder.append(END_SAMPLE_MD).append("\n");

        return examplesBuilder.toString();
    }

    public boolean exist() {
        var hasAnyExample = hasCliExample.test(rpcMethodDefinition)
                || hasJavaExample.test(rpcMethodDefinition)
                || hasPythonExample.test(rpcMethodDefinition);
        return hasAnyExample;
    }

    public static String getJavaBoilerplateSource() {
        if (javaBoilerplateSource == null) {
            String sourcePath = JAVA_EXAMPLES_DIR + File.separator + "BaseJavaExample.java";
            File sourceFile = new File(sourcePath);
            StringBuilder displayedSource = new StringBuilder(getRawContent(sourceFile).trim());
            displayedSource.append("\n");
            javaBoilerplateSource = displayedSource.toString();
        }
        return javaBoilerplateSource;
    }

    private static String getRawContent(File sourceFile) {
        return toText.apply(sourceFile.toPath());
    }
}
