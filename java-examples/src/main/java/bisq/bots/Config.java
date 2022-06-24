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
package bisq.bots;


import joptsimple.OptionParser;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import static java.lang.Boolean.FALSE;
import static java.lang.System.*;


/**
 * Parses program arguments common to Java bot examples.
 */
@Slf4j
@Getter
public class Config {

    private final String host;
    private final int port;
    private final String password;
    private final String walletPassword;
    private final String conf;
    private final boolean dryRun;
    // This is an experimental option for simulating and automating protocol payment steps during bot development.
    // Be extremely careful in its use;  You do not want to "simulate" payments when API daemon is connected to mainnet.
    private final boolean simulatePaymentSteps;

    public Config(String[] args, String defaultPropertiesFilename) {
        var parser = new OptionParser();
        var helpOpt = parser.accepts("help", "Print this help text")
                .forHelp();
        var hostOpt = parser.accepts("host", "API daemon hostname or IP")
                .withRequiredArg()
                .defaultsTo("localhost");
        var portOpt = parser.accepts("port", "API daemon port")
                .withRequiredArg()
                .ofType(Integer.class)
                .defaultsTo(9998);
        var passwordOpt = parser.accepts("password", "API daemon password (required)")
                .withRequiredArg();
        var walletPasswordOpt = parser.accepts("wallet-password", "API wallet password (required)")
                .withRequiredArg();
        var confOpt = parser.accepts("conf", "Bot configuration file (required)")
                .withRequiredArg();
        var dryRunOpt = parser.accepts("dryrun", "Pretend to take an offer (default=false)")
                .withRequiredArg()
                .ofType(boolean.class)
                .defaultsTo(FALSE);
        var simulateRegtestPaymentStepsOpt =
                parser.accepts("simulate-regtest-payment", "Simulate regtest payment steps (default=false)")
                        .withOptionalArg()
                        .ofType(boolean.class)
                        .defaultsTo(FALSE);

        var options = parser.parse(args);
        if (options.has(helpOpt)) {
            printHelp(parser, out);
            exit(0);
        }

        this.host = options.valueOf(hostOpt);
        this.port = options.valueOf(portOpt);

        this.password = options.valueOf(passwordOpt);
        if (password == null) {
            log.error("Missing required '--password=<api-password>' option");
            printHelp(parser, err);
            exit(1);
        }

        this.walletPassword = options.valueOf(walletPasswordOpt);
        if (walletPassword == null) {
            log.error("Missing required '--wallet-password=<wallet-password>' option");
            printHelp(parser, err);
            exit(1);
        }

        if (!options.has(confOpt)) {
            this.conf = defaultPropertiesFilename;
        } else {
            this.conf = options.valueOf(confOpt);
            if (!(new File(conf).exists())) {
                log.error("Invalid '--conf=<configuration file>' option:  external file does not exist.");
                printHelp(parser, err);
                exit(1);
            }
        }

        this.dryRun = options.valueOf(dryRunOpt);
        this.simulatePaymentSteps = options.valueOf(simulateRegtestPaymentStepsOpt);

        if (dryRun && simulatePaymentSteps) {
            log.error("""
                    The '--dryrun` and '--simulate-regtest-payment' options are mutually exclusive.
                    They cannot both be true.  Use '--dryrun=true` on mainnet, to see what real offers your bot would take.
                    Use '--simulate-regtest-payment=true' on regtest, to simulate a trade to its completion.""");
            printHelp(parser, err);
            exit(1);
        }
    }

    private static void printHelp(OptionParser parser, @SuppressWarnings("SameParameterValue") PrintStream stream) {
        try {
            stream.println("Bisq RPC Client");
            stream.println();
            stream.println("Usage: ScriptName [options]");
            stream.println();
            parser.printHelpOn(stream);
            stream.println();
        } catch (IOException ex) {
            ex.printStackTrace(stream);
        }
    }
}
