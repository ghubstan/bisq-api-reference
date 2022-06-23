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
package bisq.apidoc;


import bisq.apidoc.markdown.ProtobufDefinitionParser;
import bisq.apidoc.protobuf.definition.TextBlockParser;
import bisq.apidoc.protobuf.text.ProtobufFileReader;
import bisq.apidoc.protobuf.text.TextBlock;
import fun.mingshan.markdown4j.Markdown;
import fun.mingshan.markdown4j.type.block.Block;
import fun.mingshan.markdown4j.writer.MdWriter;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static bisq.apidoc.protobuf.text.ProtobufTextBlockFactory.createTextBlocks;

/**
 * Converts Bisq .proto files into a single Slate style Markdown file:  the Bisq gRPC API Reference document.
 * <p>
 * The script generates the reference doc from .proto file in three steps:
 * <p>
 * (1)  Bisq's pb.proto and grpc.proto files are parsed into java objects representing chunks of the files' raw text,
 * i.e., TextBlocks.  Each TextBlock represents some protobuf definition, from an import statement, a comment, an
 * enum, message, rpc-method, to a grpc service definition.
 * <p>
 * (2)  The list of TextBlocks produced by step 1 are parsed into java POJOs -- ProtobufDefinitions -- containing
 * descriptions of the .proto definition.  These descriptions are sourced from comments in the .proto files, and
 * serve as the "descriptions" displayed in the final Bisq gRPC API Reference doc hosted on GitHub.
 * <p>
 * (3)  The list of ProtobufDefinitions produced by step 2 are parsed into the Markdown content blocks, and written
 * in order to a single Markdown file:  index.html.md.  The file can be written directly to your forked slate repo's
 * source directory, committed to your slate fork's remote repo, then deployed to your forked slate repo's github-pages
 * site.
 */
@SuppressWarnings("ClassCanBeRecord")
@Slf4j
public class BisqApiDocMain {

    private final Path protobufsPath;
    private final Path markdownPath;
    private final boolean failOnMissingDocumentation;

    public BisqApiDocMain(Path protobufsPath, Path markdownPath, boolean failOnMissingDocumentation) {
        this.protobufsPath = protobufsPath;
        this.markdownPath = markdownPath;
        this.failOnMissingDocumentation = failOnMissingDocumentation;
    }

    public void generateMarkdown() {
        // Transform .proto files into TextBlock objects identifying what they are:
        // block comment, line comment, grpc service, rpc method, message, enum, etc.
        List<TextBlock> pbProtoTextBlocks = getParsedProtobufTextBlocks("pb.proto");
        List<TextBlock> grpcProtoTextBlocks = getParsedProtobufTextBlocks("grpc.proto");

        // Transform TextBlock objects into ProtobufDefinition maps
        // (by name) used to generate the final API doc markdown file.
        TextBlockParser textBlockParser = new TextBlockParser(pbProtoTextBlocks,
                grpcProtoTextBlocks,
                failOnMissingDocumentation);
        textBlockParser.parse();

        // Parse the mappings of names to protobuf service definitions into Markdown content blocks.
        ProtobufDefinitionParser protobufDefinitionParser = new ProtobufDefinitionParser(textBlockParser, failOnMissingDocumentation);
        protobufDefinitionParser.parse();

        // Generate final markdown content blocks ProtobufDefinition by name mappings.
        List<Block> mdBlocks = protobufDefinitionParser.getMdBlocks();  //  createMdBlocks(textBlockParser, failOnMissingDocumentation);
        try {
            // Write the markdown content blocks to file.
            Markdown.MarkdownBuilder markdownBuilder = Markdown.builder().name("index.html");
            for (Block mdBlock : mdBlocks) {
                markdownBuilder.block(mdBlock);
            }
            Markdown markdown = markdownBuilder.build();

            // Write to markdownPath (opt) dir.
            MdWriter.write(markdownPath, markdown);

            // Write to project dir.
            // MdWriter.write(markdown);

            log.debug("Final Markdown:\n{}", markdown);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private List<TextBlock> getParsedProtobufTextBlocks(String fileName) {
        ProtobufFileReader protobufFileReader = null;
        try {
            protobufFileReader = new ProtobufFileReader(protobufsPath.resolve(fileName));
            return createTextBlocks(protobufFileReader);
        } catch (Exception ex) {
            throw new IllegalStateException("Fatal error parsing file " + fileName, ex);
        } finally {
            if (protobufFileReader != null)
                protobufFileReader.close();
        }
    }

    public static void main(String[] args) {
        OptionParser optionParser = new OptionParser();

        OptionSpec<String> protosInOpt = optionParser.accepts("protosIn", "directory path to input protobuf files")
                .withRequiredArg()
                .defaultsTo("java-examples/src/main/proto");
        OptionSpec<String> markdownOutOpt = optionParser.accepts("markdownOut", "directory path to output markdown file")
                .withRequiredArg()
                .defaultsTo("./");
        OptionSpec<Boolean> failOnMissingDocumentationOpt = optionParser.accepts("failOnMissingDocumentation",
                        "fail if needed .proto comment is missing")
                .withOptionalArg()
                .ofType(boolean.class)
                .defaultsTo(Boolean.FALSE);

        OptionSet options = optionParser.parse(args);
        Path protobufsPath = Paths.get(options.valueOf(protosInOpt));
        Path markdownPath = Paths.get(options.valueOf(markdownOutOpt));
        boolean failOnMissingDocumentation = options.valueOf(failOnMissingDocumentationOpt);

        BisqApiDocMain bisqApiDocMain = new BisqApiDocMain(protobufsPath, markdownPath, failOnMissingDocumentation);
        bisqApiDocMain.generateMarkdown();
    }
}
