package com.stagecore.player.model;

public final class CommandResult {
    public final CommandStatus status;
    public final String code;
    public final String message;

    private CommandResult(CommandStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public static CommandResult completed(String message) {
        return new CommandResult(CommandStatus.COMPLETED, "OK", message);
    }

    public static CommandResult failed(String code, String message) {
        return new CommandResult(CommandStatus.FAILED, code, message);
    }

    public static CommandResult rejected(String code, String message) {
        return new CommandResult(CommandStatus.REJECTED, code, message);
    }

    @Override
    public String toString() {
        return status + " " + code + " " + message;
    }
}
