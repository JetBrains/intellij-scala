package org.jetbrains.sbt.process.options.collecting

import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.util.execution.ParametersListUtil
import org.jetbrains.sbt.process.options.parsing.CommentsAndQuotesPreprocessor

import java.nio.file.Path
import scala.jdk.CollectionConverters.ListHasAsScala

/**
 * Collects raw JVM options from `JAVA_OPTS`, `.jvmopts`, and IDE VM options
 *
 * @see [[SbtProcessOptionsResolver]] for how JVM and sbt options are combined
 *
 * Coverage:
 * - Indirectly covered by [[org.jetbrains.sbt.process.options.SbtProcessOptionsResolverTest]] source-order JVM option
 *   cases.
 */
private[options] object JvmOptionsCollector {

  final case class CollectionResult(options: Seq[String]) {
    def ++(other: CollectionResult): CollectionResult =
      CollectionResult(options ++ other.options)
  }
  object CollectionResult {
    val empty: CollectionResult = CollectionResult(Seq.empty)
  }

  private val JavaOptsEnvVar = "JAVA_OPTS"
  private val JvmOptsFileName: String = ".jvmopts"

  def collect(
    workingDir: Path,
    vmOptionsFromSettings: Seq[String],
    env: EnvironmentVariablesData
  ): CollectionResult = {
    val fromEnvVar = readFromEnvVar(env)
    val fromFile = readFromFileInDirectory(workingDir)
    val fromSettings = CollectionResult(vmOptionsFromSettings)

    fromEnvVar ++ fromFile ++ fromSettings
  }

  private def readFromEnvVar(env: EnvironmentVariablesData): CollectionResult = {
    val envVarValue = env.getEffectiveEnvironmentValue(JavaOptsEnvVar)
    val parsed = envVarValue.map(parseJvmOptionLine)
    parsed.getOrElse(CollectionResult.empty)
  }

  private def readFromFileInDirectory(directory: Path): CollectionResult = {
    val fileLines = readLinesIfReadable(directory, JvmOptsFileName)
    parseJvmOptionLines(fileLines)
  }

  private def parseJvmOptionLine(line: String): CollectionResult = {
    parseJvmOptionLines(Seq(line))
  }

  /**
   * @param lines can be content of the `.jvmopts` file or `JAVA_OPTS` environment variable
   */
  private def parseJvmOptionLines(lines: Seq[String]): CollectionResult = {
    val cleaned = lines.flatMap(CommentsAndQuotesPreprocessor.preprocess(_).preprocessedText)
    val parsed = cleaned
      .flatMap(ParametersListUtil.parse(_, false, true).asScala.toSeq)
      .map(_.trim)
      .filter(_.nonEmpty)
    CollectionResult(parsed)
  }
}
