package org.jetbrains.sbt.process.options

import com.intellij.build.issue.{BuildIssue, BuildIssueQuickFix}
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.build.BuildReporter
import org.jetbrains.plugins.scala.settings.ShowSettingsUtilImplExt
import org.jetbrains.sbt.SbtBundle
import org.jetbrains.sbt.process.options.knownOptions.KnownSbtOptions
import org.jetbrains.sbt.process.options.parsing.model.SbtOptionsDiagnostic.{Malformed, Unrecognized}
import org.jetbrains.sbt.process.options.parsing.model.{MalformedSbtOption, SbtOptionsDiagnostic, SbtOptionsSource, UnrecognizedSbtOption}
import org.jetbrains.sbt.settings.SbtExternalSystemConfigurable

import java.nio.file.Path
import java.util.List as JList
import java.util.concurrent.CompletableFuture

/**
 * Renders sbt option diagnostics to a [[BuildReporter]].
 */
private[options] final class SbtOptionsReporter(reporter: BuildReporter) {

  def reportDiagnostics(diagnostics: Seq[SbtOptionsDiagnostic]): Unit = {
    val (unrecognized, malformed) = diagnostics.partition {
      case _: Unrecognized => true
      case _: Malformed => false
    }

    val warningData =
      unrecognized.collect {
        case diagnostic: Unrecognized => collectUnrecognizedWarningData(diagnostic)
      } ++ malformed.collect {
        case diagnostic: Malformed => collectMalformedWarningData(diagnostic)
      }.flatten

    warningData.foreach(report)
  }

  private def report(warningData: WarningData): Unit =
    warningData.quickFix match {
      case Some(quickFix) =>
        reporter.warning(new BuildIssue {
          override def getTitle: String = warningData.message

          override def getDescription: String = warningData.details

          override def getQuickFixes: JList[BuildIssueQuickFix] = JList.of(quickFix)

          override def getNavigatable(project: Project): Navigatable = null
        })
      case None =>
        reporter.warning(warningData.message, None, warningData.details)
    }

  private final case class WarningData(
    @Nls message: String,
    @Nls details: String,
    quickFix: Option[BuildIssueQuickFix] = None,
  )

  private def collectUnrecognizedWarningData(unrecognized: Unrecognized): WarningData = {
    val unrecognizedOpts = unrecognized.unrecognizedOptions
    val rawOptionsConcat = unrecognizedOpts.map(_.rawOption).mkString(", ")
    val message = SbtBundle.message("sbt.unrecognized.opts", unrecognizedOpts.size, rawOptionsConcat)
    val sourceName = renderSourceName(unrecognized.source)

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

  private def collectMalformedWarningData(malformed: Malformed): Seq[WarningData] = {
    val sourceName = renderSourceName(malformed.source)
    malformed.malformedOptions.map { malformedOption =>
      val message = SbtBundle.message("sbt.malformed.opt.source", renderMalformedOptionKey(malformedOption), sourceName)
      val details = renderMalformedOption(malformedOption, malformed)
      val quickFix = Option.when(malformed.source == SbtOptionsSource.IdeSettings)(OpenSbtSettingsQuickFix.quickFix)
      WarningData(message, withSourceAction(details, malformed.source), quickFix)
    }
  }

  @Nls
  private def renderLineWithSource(@Nls warningLine: String, @Nls sourceName: String): String =
    SbtBundle.message("sbt.unrecognized.opt.source", warningLine.stripSuffix("."), sourceName)

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

  @Nls
  private def withSourceAction(@Nls details: String, source: SbtOptionsSource): String =
    source match {
      case SbtOptionsSource.IdeSettings =>
        SbtBundle.message("sbt.malformed.opt.open.sbt.settings", details, OpenSbtSettingsQuickFix.ID)
      case _ =>
        details
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
      ShowSettingsUtilImplExt.showSettingsDialog(project, classOf[SbtExternalSystemConfigurable], SbtBundle.message("sbt.settings.sbtOptions"))
      CompletableFuture.completedFuture(null)
    }
  }
}
