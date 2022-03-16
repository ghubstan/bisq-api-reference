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
package bisq.apidoc.protobuf.text;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

import static bisq.apidoc.protobuf.ProtoParserUtil.*;
import static bisq.apidoc.protobuf.text.TextBlock.PROTOBUF_DEF_TYPE.GRPC_SERVICE_DECLARATION;
import static bisq.apidoc.protobuf.text.TextBlock.PROTOBUF_DEF_TYPE.RPC_METHOD_DECLARATION;
import static java.lang.String.format;

@Slf4j
public class GrpcServiceTextBlockFactory {

    public static TextBlock createGrpcServiceTextBlock(ProtobufFileReader fileReader, String firstLine) {
        // Verify the firstLine is declaring a protobuf grpc service.
        if (!isGrpcServiceDeclaration.test(firstLine))
            throw new IllegalArgumentException(format("First line is not a grpc service declaration: %s", firstLine));

        StringBuilder enclosingServiceTextBuilder = new StringBuilder(firstLine).append("\n");
        List<TextBlock> childBlocks = new ArrayList<>();
        fileReader.pushBracesLevelStack();
        while (!fileReader.isBracesStackEmpty()) {
            String nextTrimmedLine = fileReader.getNextTrimmedLine(true);

            // Line comments appended 1st line of gRPC service and rpc method declarations will not be supported.
            // Block and line comments describing Grpc services and rpc methods must be placed above them.

            if (isClosingProtobufDefinition.test(nextTrimmedLine)) {
                enclosingServiceTextBuilder.append(nextTrimmedLine).append("\n");
                fileReader.popBracesLevelStack();
            } else if (isComment.test(nextTrimmedLine)) {
                TextBlock commentBlock = fileReader.readCommentTextBlock(nextTrimmedLine);
                enclosingServiceTextBuilder.append(indentedText(commentBlock.getText(), fileReader.getBracesStackSize()));
                childBlocks.add(commentBlock);
            } else if (isRpcMethodDeclaration.test(nextTrimmedLine)) {
                TextBlock rpcMethodTextBlock = createRpcMethodTextBlock(fileReader, nextTrimmedLine);
                enclosingServiceTextBuilder.append(indentedText(rpcMethodTextBlock.getText(), fileReader.getBracesStackSize()));
                childBlocks.add(rpcMethodTextBlock);
            } else {
                throw new IllegalStateException(format("Invalid text found in protobuf grpc service definition: %s", firstLine));
            }
        }
        enclosingServiceTextBuilder.append("\n");
        var grpcServiceTextBlock = new TextBlock(enclosingServiceTextBuilder.toString(), GRPC_SERVICE_DECLARATION, childBlocks);
        log.trace(">>>> gRPC service TextBlock Text:\n{}", grpcServiceTextBlock.getText());
        return grpcServiceTextBlock;
    }

    private static TextBlock createRpcMethodTextBlock(ProtobufFileReader fileReader, String firstLine) {
        try {
            // Verify the firstLine is declaring a protobuf rpc method.
            if (!isRpcMethodDeclaration.test(firstLine))
                throw new IllegalArgumentException(format("First line is not a rpc method declaration: %s", firstLine));

            // Rpc methods are easy to parse:  a single line to declare it, and a second line to close it.
            // For example:
            //      rpc RegisterDisputeAgent (RegisterDisputeAgentRequest) returns (RegisterDisputeAgentReply) {
            //      }
            // Line comments appended to line 1 will not be supported.
            // Comments inside the rpc method declaration will not be supported.
            // Comments on the rpc request and response message definitions themselves are supported.

            StringBuilder rpcTextBuilder = new StringBuilder(firstLine).append("\n");
            fileReader.pushBracesLevelStack();
            String enclosingLine = fileReader.getNextTrimmedLine(true);
            if (!isClosingProtobufDefinition.test(enclosingLine))
                throw new IllegalStateException("Did not find enclosing curly brace for rpc method declaration: " + firstLine);

            fileReader.popBracesLevelStack();
            rpcTextBuilder.append(enclosingLine).append("\n");
            return new TextBlock(rpcTextBuilder.toString(), RPC_METHOD_DECLARATION);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not read enum constant from line: " + firstLine, ex);
        }
    }

}
