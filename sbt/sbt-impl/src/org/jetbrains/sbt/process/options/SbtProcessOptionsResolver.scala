package org.jetbrains.sbt.process.options

import com.intellij.execution.configuration.EnvironmentVariablesData
import org.jetbrains.annotations.{Nullable, VisibleForTesting}
import org.jetbrains.plugins.scala.build.BuildReporter
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.sbt.process.options.collecting.{JvmOptionsCollector, SbtOptionsCollector}
import org.jetbrains.sbt.process.options.parsing.SbtOptionsTextNormalizer
import org.jetbrains.sbt.process.options.parsing.model.{MalformedSbtOption, SbtOptionsDiagnostic}
import org.jetbrains.sbt.process.options.reporting.SbtOptionsDiagnosticsReporter

import java.nio.file.Path

/**
 * Public boundary for resolving sbt-related option sources into final process arguments.
 *
 * Callers outside `org.jetbrains.sbt.process.options` should use this resolver and [[SbtProcessOptions]] only.
 *
 * ## Sources
 *
 * There are two user-facing kinds of options:
 *   - JVM options, collected from `JAVA_OPTS`, `.jvmopts`, and IDE VM options.
 *   - sbt options, collected from `SBT_OPTS`, `.sbtopts`, and IDE sbt options.
 *
 * JVM options are passed to the JVM verbatim after a source collection.
 * Sbt options are interpreted first because the sbt launcher has its own option vocabulary,
 * and some launcher options are implemented by passing JVM system properties instead of launcher arguments.
 *
 * ## Pipeline
 *
 * Raw sbt option text is preprocessed for comments and quotes, normalized into logical option entries, parsed against
 * the known sbt option registry, optionally reported as unrecognized, and rendered into process arguments by
 * [[SbtProcessOptionsRenderer]].
 *
 * ## Output Buckets
 *
 * The final result is [[SbtProcessOptions]]:
 *   - [[SbtProcessOptions.allVmOptions]] contains JVM options, including JVM arguments derived from sbt options
 *     such as `-sbt-dir` becoming `-Dsbt.global.base=...`.
 *   - [[SbtProcessOptions.sbtLauncherArgs]] contains arguments passed to the sbt launcher itself, such as
 *     `-debug` becoming `--debug`.
 *
 * ## Process Targets
 *
 * Separate sbt process launches use common JVM options and launcher arguments. The interactive sbt shell also accepts
 * shell-only JVM arguments for options that are meaningful only for a shell session, for example, color handling.
 *
 * ## Diagnostics
 *
 * Sbt option diagnostics are grouped by source, so warnings can say whether the bad input came from IDE settings,
 * `SBT_OPTS`, or `.sbtopts`. Suggestions for unrecognized options are based on the known option registry.
 */
object SbtProcessOptionsResolver {

  private[sbt] final case class ParsedSbtOptionsFromSettings(
    options: Seq[String],
    malformedOptions: Seq[MalformedSbtOption]
  )

  private[sbt] def parseSbtOptionsFromSettings(rawOptionsFromSettings: String): ParsedSbtOptionsFromSettings = {
    val normalized = SbtOptionsTextNormalizer.normalize(rawOptionsFromSettings)
    ParsedSbtOptionsFromSettings(normalized.options, normalized.malformedOptions)
  }

  def resolveJavaOptions(
    workingDir: Path,
    vmOptionsFromSettings: Seq[String],
    environmentVariables: EnvironmentVariablesData
  ): Seq[String] = {
    val result = JvmOptionsCollector.collect(workingDir, vmOptionsFromSettings, environmentVariables)
    result.options
  }

  def resolveForSeparateProcess(
    workingDir: Path,
    vmOptionsFromSettings: Seq[String],
    sbtOptionsFromSettings: Seq[String],
    environmentVariables: EnvironmentVariablesData,
    additionalLauncherArgs: Seq[String],
    malformedSbtOptionsFromSettings: Seq[MalformedSbtOption] = Seq.empty
  )(implicit @Nullable reporter: BuildReporter): SbtProcessOptions = {
    val vmOptions: Seq[String] =
      resolveJavaOptions(workingDir, vmOptionsFromSettings, environmentVariables)

    val sbtProcessOptions: SbtProcessOptions =
      resolveSbtOptionsForSeparateProcess(workingDir, sbtOptionsFromSettings, environmentVariables, malformedSbtOptionsFromSettings)

    val vmOptionsAll = vmOptions ++ sbtProcessOptions.allVmOptions
    val sbtLauncherArgsAll = sbtProcessOptions.sbtLauncherArgs ++ additionalLauncherArgs
    SbtProcessOptions(vmOptionsAll, sbtLauncherArgsAll)
  }

  /**
   * Resolves only sbt-option-derived arguments for a separate sbt process.
   */
  @VisibleForTesting
  private[options] def resolveSbtOptionsForSeparateProcess(
    workingDir: Path,
    sbtOptionsFromSettings: Seq[String],
    environmentVariables: EnvironmentVariablesData,
    malformedSbtOptionsFromSettings: Seq[MalformedSbtOption] = Seq.empty
  )(implicit @Nullable reporter: BuildReporter = null): SbtProcessOptions = {
    val sbtOptions: SbtOptionsCollector.CollectionResult =
      collectSbtOptionsReportingUnresolved(workingDir, sbtOptionsFromSettings, environmentVariables, malformedSbtOptionsFromSettings)

    SbtProcessOptionsRenderer.renderForSeparateProcess(sbtOptions, projectPath(workingDir))
  }

  /**
   * Resolves only sbt-option-derived arguments for sbt shell.
   */
  def resolveSbtOptionsForShell(
    workingDir: Path,
    sbtOptionsFromSettings: Seq[String],
    environmentVariables: EnvironmentVariablesData,
    malformedSbtOptionsFromSettings: Seq[MalformedSbtOption] = Seq.empty
  )(implicit @Nullable reporter: BuildReporter): SbtProcessOptions = {
    val sbtOptions = collectSbtOptionsReportingUnresolved(workingDir, sbtOptionsFromSettings, environmentVariables, malformedSbtOptionsFromSettings)
    SbtProcessOptionsRenderer.renderForShell(sbtOptions, projectPath(workingDir))
  }

  private def projectPath(workingDir: Path): String =
    workingDir.toCanonicalPath.toString

  private def collectSbtOptionsReportingUnresolved(
    workingDir: Path,
    sbtOptionsFromSettings: Seq[String],
    environmentVariables: EnvironmentVariablesData,
    malformedSbtOptionsFromSettings: Seq[MalformedSbtOption]
  )(implicit @Nullable reporter: BuildReporter): SbtOptionsCollector.CollectionResult = {
    val sbtOptions = SbtOptionsCollector.collect(sbtOptionsFromSettings, workingDir, environmentVariables, malformedSbtOptionsFromSettings)
    reportDiagnostics(sbtOptions.diagnostics)
    sbtOptions
  }

  private def reportDiagnostics(
    diagnostics: Seq[SbtOptionsDiagnostic]
  )(implicit @Nullable buildReporter: BuildReporter): Unit = {
    if (buildReporter != null) {
      val reporter = new SbtOptionsDiagnosticsReporter(buildReporter)
      reporter.reportDiagnostics(diagnostics)
    }
  }
}
