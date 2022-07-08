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

import bisq.apidoc.protobuf.definition.*;
import fun.mingshan.markdown4j.type.block.Block;
import fun.mingshan.markdown4j.type.block.SlateTableBlock;
import fun.mingshan.markdown4j.type.block.StringBlock;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Comparator.comparing;

/**
 * Transforms maps of ProtobufDefinitions into Markdown blocks for the final API doc markdown file.
 * <p>
 * A Markdown "Block" can be as small as a link, up to a large chunk of text describing a protobuf message
 * with nested tables of enums and fields.  What matters is that lists of blocks are written in proper order.
 * <p>
 * Some notes about Slate Markdown style follows.
 * <p>
 * Only level 1 and 2 headers will appear in the table of contents.
 * <p>
 * Internal and External Links:
 * <p>
 * This is an [internal link](#error-code-definitions)
 * Put these in slate tbl 'type' columns:
 * [PaymentAccount](#paymentaccount)
 * [PaymentAccountPayload](#paymentaccountpayload)
 * <p>
 * This is an [external link](http://google.com).
 * <p>
 * There are minor differences between the fun.mingshan.markdown4j type style, and slate style.
 * See SlateTableBlockEncoder.
 */
@Slf4j
public class ProtobufDefinitionParser {

    private static final String CONSTANT = "Constant";
    private static final String DESCRIPTION = "Description";
    private static final String REQUEST = "Request";
    private static final String RESPONSE = "Response";
    private static final String VALUE = "Value";

    private final Predicate<String> invalidRpcMessageType = (type) ->
            type == null || !(type.equals(REQUEST) || type.equals(RESPONSE));

    // Maps of ProtobufDefinitions produced by TextBlockParser.
    private final Map<String, EnumDefinition> globalEnumDefinitions;
    private final Map<String, MessageDefinition> globalMessageDefinitions;
    private final Map<String, GrpcServiceDefinition> grpcServiceDefinitions;

    // All API Markdown blocks in grpc-services, grpc-messages, grpc-enums order.
    private final List<Block> mdBlocks;

    // A true value will for an exception when a ProtobufDefinition does not have a description.
    private final boolean failOnMissingDocumentation;

    public ProtobufDefinitionParser(TextBlockParser textBlockParser, boolean failOnMissingDocumentation) {
        this.failOnMissingDocumentation = failOnMissingDocumentation;
        this.globalEnumDefinitions = textBlockParser.getGlobalEnumDefinitions();
        this.globalMessageDefinitions = textBlockParser.getGlobalMessageDefinitions();
        this.grpcServiceDefinitions = textBlockParser.getGrpcServiceDefinitions();
        this.mdBlocks = new ArrayList<>();
    }

    /**
     * Transform ProtobufDefinition pojos into Markdown blocks.
     * <p>
     * Get the generated md blocks by calling getMdBlocks().
     */
    public void parse() {
        parseLeadingBlocks();
        parseGrpcServiceBlocks();
        parseGrpcDependencyBlocks();
        Template errorsTemplate = new Template("errors.md");
        Block errorsBlock = StringBlock.builder().content(errorsTemplate.getContent()).build();
        mdBlocks.add(errorsBlock);
    }

    /**
     * Returns the complete list of Markdown content Blocks produced by the parse()
     * method in the following order: grpc-services, grpc-message, grpc-enums.
     */
    public List<Block> getMdBlocks() {
        return this.mdBlocks;
    }

    /**
     * Transforms gRPC service definitions into Markdown blocks.
     */
    private void parseGrpcServiceBlocks() {
        List<Block> serviceBlocks = new ArrayList<>();
        grpcServiceDefinitions.forEach((key, serviceDefinition) -> {
            String serviceDescription = serviceDefinition.description();
            if (shouldFail(serviceDescription))
                throw new IllegalStateException(
                        format("Cannot generate markdown because %s service description is missing.",
                                serviceDefinition.name()));

            Block serviceBlock = getServiceDefinitionBlock(serviceDefinition);
            serviceBlocks.add(serviceBlock);
            List<Block> rpcMethodBlocks = getRpcMethodBlocks(serviceDefinition);
            serviceBlocks.addAll(rpcMethodBlocks);
        });
        mdBlocks.addAll(serviceBlocks);
    }

    /**
     * Returns a Markdown block for gRPC service declaration.
     */
    private Block getServiceDefinitionBlock(GrpcServiceDefinition serviceDefinition) {
        String name = serviceDefinition.name();
        Map<String, String> serviceTemplateVars = new HashMap<>() {{
            put("service.name", name);
            put("service.description", serviceDefinition.description());
        }};
        Template serviceTemplate = new Template("grpc-service.md",
                serviceTemplateVars,
                failOnMissingDocumentation);
        return StringBlock.builder().content(serviceTemplate.getContent()).build();
    }

    /**
     * Returns list of Markdown blocks for an rpc method definition pojo.
     * <p>
     * An rpc method declaration is one line specifying the method name, plus the request & response message names.
     * For example:
     * <pre>
     * rpc GetMethodHelp (GetMethodHelpRequest) returns (GetMethodHelpReply)
     * The rpc method name is "GetMethodHelp".
     * The request message name is "GetMethodHelpRequest".
     * The response message name is "GetMethodHelpReply".
     * </pre>
     * This method returns md blocks for the method name, the request message, all the request message's enums and
     * fields, the response message, and all the response message's enums and fields, in that order.
     */
    private List<Block> getRpcMethodBlocks(GrpcServiceDefinition serviceDefinition) {
        List<Block> rpcMethodBlocks = new ArrayList<>();
        Map<String, RpcMethodDefinition> rpcMethodDefinitions = serviceDefinition.rpcMethodDefinitions();
        List<RpcMethodDefinition> sortedRpcMethodDefinitions = rpcMethodDefinitions.values().stream()
                .sorted(comparing(RpcMethodDefinition::name))
                .collect(Collectors.toList());
        sortedRpcMethodDefinitions.forEach(m -> {
            rpcMethodBlocks.add(getRpcMethodBlock(m));
            rpcMethodBlocks.addAll(getRpcMessageBlocks(m));
        });
        return rpcMethodBlocks;
    }

    /**
     * Returns a Markdown block for the given rpc method definition with optional code examples.
     */
    private StringBlock getRpcMethodBlock(RpcMethodDefinition methodDefinition) {
        Map<String, String> methodTemplateVars = new HashMap<>() {{
            put("method.name", methodDefinition.name());
            put("method.description", methodDefinition.description());
        }};
        Template rpcMethodTemplate = new Template("rpc-method.md",
                methodTemplateVars,
                failOnMissingDocumentation);
        Optional<StringBlock> examplesBlock = getRpcMethodCodeExamplesBlock(methodDefinition);
        return examplesBlock.isPresent()
                ? toBlockWithCodeExamples.apply(rpcMethodTemplate, examplesBlock.get())
                : StringBlock.builder().content(rpcMethodTemplate.getContent()).build();
    }

    /**
     * Transforms an rpc method definition Template into a StringBlock containing the optional
     * code examples StringBlock's content, inserted at the second line of the original content.
     */
    private final BiFunction<Template, StringBlock, StringBlock> toBlockWithCodeExamples = (rpcMethodTemplate, examplesBlock) ->
            StringBlock.builder().content(rpcMethodTemplate.getContentWithCodeExamples(examplesBlock.toMd())).build();

    /**
     * Return the optional rpc method definition code examples block, if present.
     */
    private Optional<StringBlock> getRpcMethodCodeExamplesBlock(RpcMethodDefinition rpcMethodDefinition) {
        // Use working code in the java/python/shell examples directories.
        CodeExamples codeExamples = new CodeExamples(rpcMethodDefinition);
        if (codeExamples.exist()) {
            StringBlock examplesBlock = StringBlock.builder().content(codeExamples.getContent()).build();
            return Optional.of(examplesBlock);
        } else {
            return Optional.empty();
        }
    }

    /**
     * Returns a list of Markdown blocks for an rpc method's request message, all the request message's enums
     * and fields, the response message, and all the response message's enums and fields, in that order.
     */
    private List<Block> getRpcMessageBlocks(RpcMethodDefinition rpcMethodDefinition) {
        // All rpc methods have a request message and a response message.
        List<Block> messageBlocks = new ArrayList<>();

        // Append top level request msg declaration table block.
        MessageDefinition requestMessageDefinition = rpcMethodDefinition.requestMessageDefinition();
        Block requestBlock = getRpcMessageBlock(requestMessageDefinition, REQUEST);
        messageBlocks.add(requestBlock);

        // Append request msg enum and field table blocks.
        messageBlocks.addAll(getRpcEnumAndMessageBlocks(requestMessageDefinition));

        // Append top level response msg declaration table block.
        MessageDefinition responseMessageDefinition = rpcMethodDefinition.responseMessageDefinition();
        Block responseBlock = getRpcMessageBlock(responseMessageDefinition, RESPONSE);
        messageBlocks.add(responseBlock);

        // Append response msg enum and field table blocks.
        messageBlocks.addAll(getRpcEnumAndMessageBlocks(responseMessageDefinition));

        return messageBlocks;
    }

    /**
     * Returns list of Markdown blocks for an rpc message's enums and fields.
     */
    private List<Block> getRpcEnumAndMessageBlocks(MessageDefinition rpcMessageDefinition) {
        List<Block> messageBlocks = new ArrayList<>();
        // Append msg enum table blocks here.
        if (!rpcMessageDefinition.enums().isEmpty()) {
            List<Block> enumBlocks = getMessageEnumBlocks(rpcMessageDefinition);
            messageBlocks.addAll(enumBlocks);
        }
        // Append msg field table blocks here.
        if (!rpcMessageDefinition.fields().isEmpty()) {
            Block requestFieldBlock = getFieldTableBlock(rpcMessageDefinition);
            messageBlocks.add(requestFieldBlock);
        }
        return messageBlocks;
    }

    /**
     * Returns a Markdown block for an rpc request or response message.
     */
    private StringBlock getRpcMessageBlock(MessageDefinition rpcMessageDefinition,
                                           String rpcMessageType) {
        if (invalidRpcMessageType.test(rpcMessageType))
            throw new IllegalArgumentException(
                    format("Invalid rpcMessageType param value '%s', must be one of %s or %s",
                            rpcMessageType,
                            REQUEST,
                            RESPONSE));

        boolean hasFields = rpcMessageDefinition.fields().size() > 0;
        String description = hasFields
                ? rpcMessageDefinition.description()
                : format("%s%nThis %s has no parameters.", rpcMessageDefinition.description(), rpcMessageType);
        Map<String, String> templateVars = new HashMap<>() {{
            put("rpc.message.type", rpcMessageType);
            put("rpc.message.name", rpcMessageDefinition.name());
            put("rpc.message.description", description);
        }};
        Template template = new Template("rpc-message.md", templateVars, failOnMissingDocumentation);
        return StringBlock.builder().content(template.getContent()).build();
    }

    /**
     * Returns a Markdown block for a global enum definition.
     */
    private Block getGlobalEnumBlock(EnumDefinition enumDefinition) {
        return getEnumTableBlock(enumDefinition, true);
    }

    /**
     * Returns a list of Markdown blocks containing all the (local) enums declared within a message.
     */
    private List<Block> getMessageEnumBlocks(MessageDefinition messageDefinition) {
        List<Block> enumBlocks = new ArrayList<>();
        messageDefinition.enums().values().forEach(enumDefinition -> {
            Block mdBlock = getEnumTableBlock(enumDefinition, false);
            enumBlocks.add(mdBlock);
        });
        return enumBlocks;
    }

    /**
     * Returns a Markdown block for an enum with an md table of constants.
     */
    private Block getEnumTableBlock(EnumDefinition enumDefinition, boolean isGlobal) {
        Block enumConstantBlock = getEnumConstantTableBlock(enumDefinition);
        Map<String, String> templateVars = new HashMap<>() {{
            put("enum.name", enumDefinition.name());
            put("enum.description", enumDefinition.description());
            put("enum.constant.tbl", enumConstantBlock.toMd());
        }};
        String templateFileName = isGlobal ? "global-enum.md" : "message-enum.md";
        Template template = new Template(templateFileName, templateVars, failOnMissingDocumentation);
        return StringBlock.builder().content(template.getContent()).build();
    }

    /**
     * Returns a Markdown block containing a table of fields (name, type, description).
     */
    private Block getFieldTableBlock(MessageDefinition messageDefinition) {
        if (messageDefinition.fields().size() == 0)
            throw new IllegalArgumentException("Cannot build Field md table block from empty field map.");

        return new FieldTableBlockBuilder(messageDefinition, failOnMissingDocumentation).build();
    }

    /**
     * Cache fields and enums with custom data types, then transform them into Markdown blocks, and
     * append those md blocks to the end of the API doc under sections "gRPC Messages" and "gRPC Enums".
     */
    private void parseGrpcDependencyBlocks() {
        parseGrpcMessagesHeaderBlock();

        // Make sure all ProtobufDefinition dependencies are cached in the grpcMessageDefinitions
        // list before creating any md field blocks.
        GrpcDependencyCache grpcDependencyCache = new GrpcDependencyCache(globalEnumDefinitions,
                globalMessageDefinitions,
                grpcServiceDefinitions)
                .load();
        parseGrpcMessageDependencies(grpcDependencyCache);  // msg deps have to be parsed first
        parseGrpcEnumsHeaderBlock();
        parseGrpcEnumDependencies(grpcDependencyCache);     // enum deps are found in the msg deps
    }

    private void parseGrpcMessagesHeaderBlock() {
        Template template = new Template("grpc-messages.md");
        Block sectionTitleBlock = StringBlock.builder().content(template.getContent()).build();
        mdBlocks.add(sectionTitleBlock);
    }

    private void parseGrpcMessageDependencies(GrpcDependencyCache dependencyCache) {
        dependencyCache.getGrpcMessageDependencies().forEach(m -> {
            Block messageBlock = getMessageBlock(m);
            mdBlocks.add(messageBlock);
            if (!m.fields().isEmpty()) {
                Block fieldsBlock = getFieldTableBlock(m);
                mdBlocks.add(fieldsBlock);
            }
        });
    }

    private void parseGrpcEnumsHeaderBlock() {
        Template template = new Template("grpc-enums.md");
        StringBlock mdBlock = StringBlock.builder().content(template.getContent()).build();
        mdBlocks.add(mdBlock);
    }

    private void parseGrpcEnumDependencies(GrpcDependencyCache dependencyCache) {
        dependencyCache.getGrpcEnumDependencies().forEach(e -> {
            log.debug("Create md block for {}", e.name());
            Block enumBlock = getGlobalEnumBlock(e);
            mdBlocks.add(enumBlock);
        });
    }

    private StringBlock getMessageBlock(MessageDefinition messageDefinition) {
        Map<String, String> templateVars = new HashMap<>() {{
            put("message.name", messageDefinition.name());
            put("message.description", messageDefinition.description());
        }};
        Template template = new Template("message.md", templateVars, failOnMissingDocumentation);
        return StringBlock.builder().content(template.getContent()).build();
    }

    private Block getEnumConstantTableBlock(EnumDefinition enumDefinition) {
        if (enumDefinition.constants().size() == 0)
            throw new IllegalArgumentException("Cannot build Enum md table block without enum constants.");

        List<SlateTableBlock.TableRow> rows = new ArrayList<>();
        List<EnumConstantDefinition> constants = enumDefinition.constants().values().stream().toList();
        constants.forEach(c -> {
            SlateTableBlock.TableRow row = new SlateTableBlock.TableRow();
            row.setColumns(asList(c.name(),
                    Integer.toString(c.value()),
                    c.description()));
            rows.add(row);
        });
        return SlateTableBlock.builder()
                .titles(asList(CONSTANT, VALUE, DESCRIPTION))
                .rows(rows)
                .build();
    }

    private void parseLeadingBlocks() {
        Template headerTemplate = new Template("header.md");
        Block headerBlock = StringBlock.builder()
                .content(headerTemplate.getContent())
                .build();
        mdBlocks.add(headerBlock);

        Template introTemplate = new Template("introduction.md");
        Block introBlock = StringBlock.builder()
                .content(introTemplate.getContent())
                .build();
        mdBlocks.add(introBlock);

        Template warningsTemplate = new Template("warnings.md");
        Block warningsBlock = StringBlock.builder()
                .content(warningsTemplate.getContent())
                .build();
        mdBlocks.add(warningsBlock);

        Template examplesSetupTemplate = new Template("examples-setup.md");
        Block examplesSetupBlock = StringBlock.builder()
                .content(examplesSetupTemplate.getContent())
                .build();
        mdBlocks.add(examplesSetupBlock);

        Template authenticationTemplate = new Template("authentication.md");
        Block authenticationBlock = StringBlock.builder()
                .content(authenticationTemplate.getContent())
                .build();
        mdBlocks.add(authenticationBlock);
    }

    /**
     * Return true if failOnMissingDocumentation is true and description is not specified.
     * TODO Fully implement this fail-on-missing-comments rule only after .proto commenting
     *  has reached an advanced stage.
     */
    private boolean shouldFail(String description) {
        return failOnMissingDocumentation && (description == null || description.isBlank());
    }
}
