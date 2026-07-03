package org.jetbrains.sbt.process.options.reporting

import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.build.BuildReporter
import org.jetbrains.plugins.scala.settings.ShowSettingsUtilImplExt
import org.jetbrains.sbt.SbtBundle
import org.jetbrains.sbt.process.options.knownOptions.KnownSbtOptions
import org.jetbrains.sbt.process.options.parsing.model.SbtOptionsDiagnostic.{Malformed, Unrecognized}
import org.jetbrains.sbt.process.options.parsing.model.{MalformedSbtOption, SbtOptionsDiagnostic, SbtOptionsSource, UnrecognizedSbtOption}
import org.jetbrains.sbt.settings.SbtExternalSystemConfigurable

import java.nio.file.Path
import java.util.concurrent.CompletableFuture

/**
 * Renders sbt option diagnostics to a [[BuildReporter]].
 */
//noinspection ApiStatus,UnstableApiUsage
private[sbt] final class SbtOptionsDiagnosticsReporter(reporter: BuildReporter) {

  def reportDiagnostics(diagnostics: Seq[SbtOptionsDiagnostic]): Unit =
    SbtOptionsDiagnosticsReporter.reportWarnings(
      reporter,
      SbtOptionsDiagnosticsReporter.collectWarnings(diagnostics)
    )
}

private[sbt] object SbtOptionsDiagnosticsReporter {

  private def collectWarnings(diagnostics: Seq[SbtOptionsDiagnostic]): Seq[SbtOptionsWarningData] = {
    val (unrecognized, malformed) = diagnostics.partition {
      case _: Unrecognized => true
      case _: Malformed => false
    }

    val unrecognisedWarnings = unrecognized.collect {
      case diagnostic: Unrecognized => collectUnrecognizedWarningData(diagnostic)
    }

    val malformedWarnings = malformed.collect {
      case diagnostic: Malformed => collectMalformedWarningData(diagnostic)
    }.flatten

    val allWarnings = unrecognisedWarnings ++ malformedWarnings
    allWarnings
  }

  def reportWarnings(reporter: BuildReporter, warnings: Seq[SbtOptionsWarningData]): Unit =
    warnings.foreach { warning =>
      if (warning.quickFixes.isEmpty) {
        reporter.warning(warning.title, None, warning.details)
      } else {
        reporter.warning(new SbtOptionsBuildIssue(warning))
      }
    }

  private def collectUnrecognizedWarningData(unrecognized: Unrecognized): SbtOptionsWarningData = {
    val unrecognizedOpts = unrecognized.unrecognizedOptions
    val rawOptionsConcat = unrecognizedOpts.map(_.rawOption).mkString(", ")
    val sourceName = renderSourceName(unrecognized.source)
    val message = SbtBundle.message("sbt.unrecognized.opts.source", unrecognizedOpts.size, rawOptionsConcat, sourceName)

    val allOptionsHelpersText = KnownSbtOptions.AllHelperMessages.mkString("\n", "\n", "")
    val detailsLines =
      renderUnrecognizedSourceDetails(unrecognizedOpts, unrecognized.source, unrecognized.optionsFile) +:
        Seq(SbtBundle.message("sbt.available.opts", allOptionsHelpersText))
    SbtOptionsWarningData(message, detailsLines.mkString("\n"), quickFixesFor(unrecognized.source))
  }

  private def collectMalformedWarningData(malformed: Malformed): Seq[SbtOptionsWarningData] = {
    val sourceName = renderSourceName(malformed.source)
    val quickFixes = quickFixesFor(malformed.source)
    malformed.malformedOptions.map { malformedOption =>
      val message = SbtBundle.message("sbt.malformed.opt.source", renderMalformedOptionKey(malformedOption), sourceName)
      val details = renderMalformedOption(malformedOption, malformed)
      SbtOptionsWarningData(message, renderSourceDetails(details, malformed.source, optionsFile = None), quickFixes)
    }
  }

  private def quickFixesFor(source: SbtOptionsSource): Seq[BuildIssueQuickFix] =
    source match {
      case SbtOptionsSource.IdeSettings => Seq(OpenSbtSettingsQuickFix.quickFix)
      case _ => Seq.empty
    }

  @Nls
  private def renderUnrecognizedSourceDetails(
    unrecognizedOptions: Seq[UnrecognizedSbtOption],
    source: SbtOptionsSource,
    optionsFile: Option[Path]
  ): String = {
    val warningLines = unrecognizedOptions.map(renderUnrecognizedOption)
    source match {
      case SbtOptionsSource.OptionsFile =>
        optionsFile.fold(warningLines.mkString("\n")) { file =>
          val fileUri = renderUriForIdeLinking(file)
          val lines = unrecognizedOptions.zip(warningLines).map { case (option, warningLine) =>
            SbtBundle.message("sbt.options.in.file", warningLine.stripSuffix("."), fileUri, option.lineNumber)
          }
          lines.mkString("\n")
        }
      case SbtOptionsSource.IdeSettings =>
        SbtBundle.message("sbt.options.open.sbt.settings", warningLines.mkString("\n"), OpenSbtSettingsQuickFix.ID)
      case _ =>
        warningLines.mkString("\n")
    }
  }

  @Nls
  private def renderUnrecognizedOption(unrecognizedOption: UnrecognizedSbtOption): String =
    unrecognizedOption match {
      case UnrecognizedSbtOption(rawOption, Some(suggestedHelper), _) =>
        SbtBundle.message("sbt.unrecognized.opt.with.suggestion", rawOption, suggestedHelper)
      case UnrecognizedSbtOption(rawOption, None, _) =>
        SbtBundle.message("sbt.unrecognised.opt", rawOption)
    }

  @Nls
  private def renderSourceDetails(
    @Nls details: String,
    source: SbtOptionsSource,
    optionsFile: Option[Path]
  ): String =
    source match {
      case SbtOptionsSource.OptionsFile =>
        optionsFile.fold(details) { file =>
          SbtBundle.message("sbt.options.in.file", details.stripSuffix("."), renderUriForIdeLinking(file))
        }
      case SbtOptionsSource.IdeSettings =>
        SbtBundle.message("sbt.options.open.sbt.settings", details, OpenSbtSettingsQuickFix.ID)
      case _ =>
        details
    }

  @Nls
  private def renderMalformedOption(malformedOption: MalformedSbtOption, malformed: Malformed): String = {
    val quoteKind = renderQuoteKind(malformedOption.unclosedQuote)
    malformed.source match {
      case SbtOptionsSource.OptionsFile =>
        malformed.optionsFile.fold(
          SbtBundle.message("sbt.malformed.opt.unbalanced.quote", malformedOption.lineNumber, quoteKind)
        ) { file =>
          SbtBundle.message(
            "sbt.malformed.opt.unbalanced.quote.in.file",
            renderUriForIdeLinking(file),
            malformedOption.lineNumber,
            quoteKind,
            malformedOption.lineContent
          )
        }
      case _ =>
        SbtBundle.message("sbt.malformed.opt.unbalanced.quote", malformedOption.lineNumber, quoteKind)
    }
  }

  @Nls
  private def renderQuoteKind(quote: Char): String =
    quote match {
      case '"' => SbtBundle.message("sbt.options.quote.double")
      case '\'' => SbtBundle.message("sbt.options.quote.single")
      case _ => quote.toString
    }

  @Nls
  private def renderMalformedOptionKey(malformedOption: MalformedSbtOption): String = {
    val trimmed = malformedOption.lineContent.trim
    if (trimmed.isEmpty)
      SbtBundle.message("sbt.malformed.opt.unknown")
    else {
      val prefixBeforeUnclosedQuote = findUnclosedQuoteStart(trimmed, malformedOption.unclosedQuote)
        .fold(trimmed)(trimmed.take)
      val tokens = prefixBeforeUnclosedQuote.trim.split("\\s+").filter(_.nonEmpty).toSeq
      val rawCandidate = tokens match {
        case init :+ "=" if init.nonEmpty => s"${init.last}="
        case _ :+ last => last
        case _ => SbtBundle.message("sbt.malformed.opt.unknown")
      }

      rawCandidate.indexOf('=') match {
        case -1 => rawCandidate
        case equalsIndex => rawCandidate.take(equalsIndex + 1)
      }
    }
  }

  private def findUnclosedQuoteStart(line: String, unclosedQuote: Char): Option[Int] = {
    var activeQuote = Option.empty[(Char, Int)]
    var index = 0

    while (index < line.length) {
      val char = line.charAt(index)
      activeQuote match {
        case Some((quote, _)) if char == quote =>
          activeQuote = None
        case None if char == '"' || char == '\'' =>
          activeQuote = Some((char, index))
        case _ =>
      }
      index += 1
    }

    activeQuote.collect {
      case (`unclosedQuote`, quoteIndex) => quoteIndex
    }
  }

  @Nls
  private def renderSourceName(source: SbtOptionsSource): String =
    source match {
      case SbtOptionsSource.IdeSettings => SbtBundle.message("sbt.options.source.ide.settings")
      case SbtOptionsSource.OptionsFile => SbtBundle.message("sbt.options.source.options.file")
      case SbtOptionsSource.EnvironmentVariable => SbtBundle.message("sbt.options.source.environment.variable")
    }

  private def renderUriForIdeLinking(path: Path): String =
    // Use URI form so IntelliJ IDEA's default console filters can highlight the location as a link.
    path.toAbsolutePath.toUri.toString
}

private object OpenSbtSettingsQuickFix {
  val ID: String = "open_sbt_settings"

  val quickFix: BuildIssueQuickFix = new BuildIssueQuickFix {
    override def getId: String = ID

    override def runQuickFix(project: Project, dataContext: DataContext): CompletableFuture[?] = {
      // TODO: Open the concrete source-specific settings in the future, e.g. an sbt run configuration when options come from there.
      //NOTE: trimming as "Sbt options" UI value has leading `&` mnemonic marker.
      // It's automatically replaces with space. It's not a big issue, but visually it looks a little strange why the search text has a leading space
      val searchText = SbtBundle.message("sbt.settings.sbtOptions").trim
      ShowSettingsUtilImplExt.showSettingsDialog(project, classOf[SbtExternalSystemConfigurable], searchText)
      CompletableFuture.completedFuture(null)
    }
  }
}
