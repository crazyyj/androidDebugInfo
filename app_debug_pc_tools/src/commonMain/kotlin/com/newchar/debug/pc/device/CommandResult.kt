package com.newchar.debug.pc.device

data class CommandResult(
    val exitCode: Int,
    val output: String,
    val error: String = ""
) {
    val isSuccess: Boolean get() = exitCode == 0
    
    fun throwIfFailed(message: String = "Command failed") {
        if (!isSuccess) {
            throw RuntimeException("$message (exit code $exitCode): ${error.ifEmpty { output }}")
        }
    }
    
    companion object {
        fun success(output: String = "") = CommandResult(0, output)
        
        fun failure(exitCode: Int, error: String) = CommandResult(exitCode, "", error)
    }
}
