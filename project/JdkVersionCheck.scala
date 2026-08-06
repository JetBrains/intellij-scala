import org.jetbrains.sbtidea.{PluginLogger, SbtPluginLogger}
import sbt.Logger
import sbt.Keys.TaskStreams

object JdkVersionCheck {
  private val UnknownVersion = "unknown"

  def warnIfNotRequiredJdk(log: Logger, minJdkVersion: String): Unit = {
    val version = sys.props.getOrElse("java.specification.version", UnknownVersion)
    try {
      val currentMajor = parseMajorJdkVersion(version)
      val requiredMajor = parseMajorJdkVersion(minJdkVersion)
      if (currentMajor < requiredMajor) {
        log.warn(
          s"""!!!
             |!!! JDK $minJdkVersion or newer is required to work with this project (see README.md), but current sbt is running on $version.
             |!!! Set JAVA_HOME or use .sbtopts -java-home to point to JDK $minJdkVersion+
             |!!!""".stripMargin
        )
      }
    } catch {
      case ex: NumberFormatException =>
        log.warn("Can't parse JDK version")
        log.trace(ex)
    }
  }

  def warnIfNotRequiredJdk(streams: TaskStreams, requiredSpecVersion: String): Unit = {
    PluginLogger.bind(new SbtPluginLogger(streams))
    warnIfNotRequiredJdk(streams.log, requiredSpecVersion)
  }

  @throws[NumberFormatException]
  private def parseMajorJdkVersion(version: String): Int = {
    val trimmed = version.trim
    if (trimmed.startsWith("1."))
      trimmed.stripPrefix("1.").takeWhile(_.isDigit).toInt
    else
      trimmed.takeWhile(_.isDigit).toInt
  }
}
