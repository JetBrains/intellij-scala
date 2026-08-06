import java.nio.file.{Files, Path}

scalaVersion := "2.13.18"

val writeSbtGlobalBasePath = taskKey[Unit]("Writes the sbt.global.base system property to the configured output file")
val writeSbtGlobalLocalCachePath = taskKey[Unit]("Writes the sbt.global.localcache system property to the configured output file")
val writeQuotedJvmOptsPath = taskKey[Unit]("Writes the quoted.jvmopts.path system property to the configured output file")
val writeOptionModelRegressionProperties = taskKey[Unit]("Writes JVM properties used by sbt option model regression tests")

val missingPropertyValue = "<missing>"

val optionModelRegressionPropertyNames = Seq(
  "sbt.global.base",
  "sbt.global.localcache",
  "quoted.jvmopts.path",
  "sbt.task.timings",
  "sbt.task.timings.on.shutdown",
  "sbt.boot.directory",
  "sbt.ivy.home",
  "sbt.color",
  "sbt.log.noformat",
  "option.source.java",
  "option.source.sbt"
)

def configuredOutputFile: Path = {
  val outputFilePropertyName = "quoted.settings.outputFile"
  Path.of(sys.props.getOrElse(
    outputFilePropertyName,
    throw new IllegalStateException(s"Missing required JVM property: $outputFilePropertyName")
  ))
}

def writePropertyToConfiguredOutputFile(propertyName: String): Unit = {
  val propertyValue = sys.props.getOrElse(
    propertyName,
    throw new IllegalStateException(s"Missing required JVM property: $propertyName")
  )

  Files.writeString(configuredOutputFile, propertyValue + System.lineSeparator())
}

def writePropertiesToConfiguredOutputFile(propertyNames: Seq[String]): Unit = {
  val lines = propertyNames.map { propertyName =>
    s"$propertyName=${sys.props.getOrElse(propertyName, missingPropertyValue)}"
  }
  Files.writeString(configuredOutputFile, lines.mkString("", System.lineSeparator(), System.lineSeparator()))
}

lazy val quotedPathOptions = (project in file("."))
  .settings(
    writeSbtGlobalBasePath := writePropertyToConfiguredOutputFile("sbt.global.base"),
    writeSbtGlobalLocalCachePath := writePropertyToConfiguredOutputFile("sbt.global.localcache"),
    writeQuotedJvmOptsPath := writePropertyToConfiguredOutputFile("quoted.jvmopts.path"),
    writeOptionModelRegressionProperties := writePropertiesToConfiguredOutputFile(optionModelRegressionPropertyNames)
  )
