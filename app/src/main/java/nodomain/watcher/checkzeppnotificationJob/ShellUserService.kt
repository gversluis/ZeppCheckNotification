package nodomain.watcher.checkzeppnotificationJob

class ShellUserService : IUserService.Stub() {
    override fun exec(command: String): String {
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
        process.waitFor()
        val out = process.inputStream.bufferedReader().readText()
        val err = process.errorStream.bufferedReader().readText()
        return (out + err).trim()
    }
}