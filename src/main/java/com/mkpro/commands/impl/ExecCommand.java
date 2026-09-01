package com.mkpro.commands.impl;

import com.mkpro.commands.Command;
import com.mkpro.core.MkProContext;
import com.mkpro.infra.ssh.SshSessionManager;
import com.mkpro.tools.ShellTools;

import java.util.List;

import static com.mkpro.ui.AnsiColors.*;

/**
 * /exec <cmd>
 * Executes command on the active remote SSH session if connected;
 * otherwise runs locally via shell.
 */
public class ExecCommand implements Command {

    @Override
    public void execute(String[] args, MkProContext context) throws Exception {
        var terminal = context.getTerminal();
        var writer = terminal.writer();

        if (args == null || args.length == 0) {
            writer.println(ANSI_YELLOW + "Usage: /exec <command>" + ANSI_RESET);
            writer.println(ANSI_DIM + "Runs <command> on the active remote SSH session if connected, or locally if not." + ANSI_RESET);
            writer.flush();
            return;
        }

        String command = String.join(" ", args).trim();
        if (command.isEmpty()) {
            writer.println(ANSI_YELLOW + "Usage: /exec <command>" + ANSI_RESET);
            writer.flush();
            return;
        }

        SshSessionManager sshManager = SshSessionManager.getInstance();
        List<SshSessionManager.SessionInfo> activeSessions = sshManager.listSessions();

        // Check if there is an active SSH session
        String activeAlias = null;
        if (sshManager.hasActiveSession("default")) {
            activeAlias = "default";
        } else {
            for (var sess : activeSessions) {
                if (sess.isConnected()) {
                    activeAlias = sess.getAlias();
                    break;
                }
            }
        }

        if (activeAlias != null) {
            // Execute on active remote SSH session
            writer.println(ANSI_BLUE + "[SSH:" + activeAlias + "] $ " + command + ANSI_RESET);
            writer.flush();
            try {
                var execResult = sshManager.executeCommand(activeAlias, command, 30);
                if (execResult.getStdout() != null && !execResult.getStdout().isEmpty()) {
                    writer.print(execResult.getStdout());
                    if (!execResult.getStdout().endsWith("\n")) {
                        writer.println();
                    }
                }
                if (execResult.getStderr() != null && !execResult.getStderr().isEmpty()) {
                    writer.print(ANSI_RED + execResult.getStderr() + ANSI_RESET);
                    if (!execResult.getStderr().endsWith("\n")) {
                        writer.println();
                    }
                }
                if (execResult.isSuccess()) {
                    writer.println(ANSI_GREEN + "[SSH:" + activeAlias + "] Exit code: 0 (" + execResult.getDurationMs() + "ms)" + ANSI_RESET);
                } else {
                    writer.println(ANSI_YELLOW + "[SSH:" + activeAlias + "] Exit code: " + execResult.getExitCode() + ANSI_RESET);
                }
            } catch (Exception e) {
                writer.println(ANSI_RED + "[SSH Execution Error] " + e.getMessage() + ANSI_RESET);
            }
        } else {
            // Execute locally
            writer.println(ANSI_DIM + "[Local] $ " + command + ANSI_RESET);
            writer.flush();
            try {
                String output = ShellTools.runCommand(command);
                if (output != null && !output.isEmpty()) {
                    writer.print(output);
                    if (!output.endsWith("\n")) {
                        writer.println();
                    }
                }
                writer.println(ANSI_GREEN + "[Local] Command completed." + ANSI_RESET);
            } catch (Exception e) {
                writer.println(ANSI_RED + "[Local Execution Error] " + e.getMessage() + ANSI_RESET);
            }
        }
        writer.flush();
    }

    @Override
    public String getName() {
        return "exec";
    }

    @Override
    public String getDescription() {
        return "Execute command on active SSH session if connected, or locally if not.";
    }
}
