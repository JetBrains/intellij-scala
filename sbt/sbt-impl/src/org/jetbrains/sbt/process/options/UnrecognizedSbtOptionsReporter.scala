package org.jetbrains.sbt.process.options

import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.build.BuildReporter
import org.jetbrains.sbt.SbtBundle
import org.jetbrains.sbt.process.options.knownOptions.KnownSbtOptions
import org.jetbrains.sbt.process.options.parsing.model.{SbtOptionsSource, UnrecognizedSbtOption, UnrecognizedSbtOptions}

/**
 * Renders grouped unrecognized-option diagnostics to a [[BuildReporter]].
 *
 * See [[SbtProcessOptionsResolver]] for  source attribution and suggestion context.
 */
private[options] final class UnrecognizedSbtOptionsReporter(reporter: BuildReporter) {

  def reportUnrecognizedOptions(unrecognised: Seq[UnrecognizedSbtOptions]): Unit = {
    val warningData = unrecognised.map(collectWarningData)
    warningData.foreach(report)
  }

  private def report(warningData: WarningData): Unit = {
    reporter.warning(warningData.message, None, warningData.details)
  }

  private final case class WarningData(
    @Nls message: String,
    @Nls details: String,
  )

  private def collectWarningData(unrecognised: UnrecognizedSbtOptions): WarningData = {
    collectWarningData(unrecognised.unrecognizedOptions, unrecognised.source)
  }

  private def collectWarningData(unrecognizedOpts: Seq[UnrecognizedSbtOption], source: SbtOptionsSource): WarningData = {
    val rawOptionsConcat = unrecognizedOpts.map(_.rawOption).mkString(", ")
    val message = SbtBundle.message("sbt.unrecognized.opts", unrecognizedOpts.size, rawOptionsConcat)
    val sourceName = renderSourceName(source)

    val warningLines = unrecognizedOpts.map {
      case UnrecognizedSbtOption(rawOption, Some(suggestedHelper)) =>
        val warningLine = SbtBundle.message("sbt.unrecognized.opt.with.suggestion", rawOption, suggestedHelper)
        renderLineWithSource(warningLine, sourceName)
      case UnrecognizedSbtOption(rawOption, None) =>
        renderLineWithSource(SbtBundle.message("sbt.unrecognised.opt", rawOption), sourceName)
    }

    val allOptionsHelpersText = KnownSbtOptions.AllHelperMessages.mkString("\n", "\n", "")
    val detailsLines = warningLines :+ SbtBundle.message("sbt.available.opts", allOptionsHelpersText)
    WarningData(message, detailsLines.mkString("\n"))
  }

  @Nls
  private def renderLineWithSource(@Nls warningLine: String, @Nls sourceName: String): String =
    SbtBundle.message("sbt.unrecognized.opt.source", warningLine.stripSuffix("."), sourceName)

  @Nls
  private def renderSourceName(source: SbtOptionsSource): String =
    source match {
      case SbtOptionsSource.IdeSettings => SbtBundle.message("sbt.options.source.ide.settings")
      case SbtOptionsSource.OptionsFile => SbtBundle.message("sbt.options.source.options.file")
      case SbtOptionsSource.EnvironmentVariable => SbtBundle.message("sbt.options.source.environment.variable")
    }
}
