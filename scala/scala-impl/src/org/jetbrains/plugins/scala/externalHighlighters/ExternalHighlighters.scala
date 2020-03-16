package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.impl.{HighlightInfo, UpdateHighlightersUtil}
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.{Document, Editor, EditorFactory}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.psi.{PsiElement, PsiFile, PsiManager, PsiWhiteSpace}
import org.jetbrains.plugins.scala.annotator.ScalaHighlightingMode
import CompilerGeneratedStateManager.HighlightingState
import org.jetbrains.plugins.scala.editor.EditorExt
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, invokeLater}
import org.jetbrains.plugins.scala.settings.ProblemSolverUtils

import scala.collection.JavaConverters._

object ExternalHighlighters {

  def updateOpenEditors(project: Project, state: HighlightingState): Unit =
    EditorFactory.getInstance().getAllEditors
      .filter(_.getProject == project)
      .foreach(applyHighlighting(project, _, state))

  def applyHighlighting(project: Project,
                        editor: Editor,
                        state: HighlightingState): Unit =
    if (ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project)) {
      for (vFile <- editor.scalaFile)
        invokeLater {
          val highlightInfos = state.getOrElse(vFile, Seq.empty)
            .map(toHighlightInfo(_, editor))

          val document = editor.getDocument
          UpdateHighlightersUtil.setHighlightersToEditor(
            project,
            document, 0, document.getTextLength,
            highlightInfos.toSeq.asJava,
            editor.getColorsScheme,
            Pass.EXTERNAL_TOOLS
          )
        }
    }

  def informWolf(project: Project, state: HighlightingState): Unit =
    if (ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project)) {
      ProblemSolverUtils.clearAllProblemsFromExternalSource(project, this)
      val wolf = WolfTheProblemSolver.getInstance(project)
      val errorFiles = state.collect {
        case (file, fileState) if fileState.exists(_.severity == HighlightSeverity.ERROR) => file
      }
      errorFiles.foreach(wolf.reportProblemsFromExternalSource(_, this))
    }

  private def toHighlightInfo(highlighting: ExternalHighlighting, editor: Editor): HighlightInfo = {
    val highlightRange =
      highlightingRange(editor.getDocument, highlighting)
        .orElse(
          offset(highlighting, editor).flatMap(findRangeToHighlight(editor, _))
        )
        .getOrElse(TextRange.EMPTY_RANGE)

    val highlightInfoType = HighlightInfo.convertSeverity(highlighting.severity)

    val message = highlighting.message
    val description: String = message.trim.stripSuffix(lineText(message))

    HighlightInfo
      .newHighlightInfo(highlightInfoType)
      .range(highlightRange)
      .descriptionAndTooltip(description)
      .group(Pass.EXTERNAL_TOOLS)
      .create()
  }

  private def findRangeToHighlight(editor: Editor, offset: Int): Option[TextRange] = {
    for {
      vFile   <- editor.scalaFile
      psiFile <- Option(PsiManager.getInstance(editor.getProject).findFile(vFile))
      element <- elementToHighlight(psiFile, offset)
    } yield {
      element.getTextRange
    }
  }

  private def elementToHighlight(file: PsiFile, offset: Int): Option[PsiElement] = {
    Option(file.findElementAt(offset))
      .flatMap {
        case ws: PsiWhiteSpace => ws.prevElementNotWhitespace
        case other             => Some(other)
      }

  }

  private def highlightingRange(doc: Document, highlighting: ExternalHighlighting): Option[TextRange] = {
    for {
      toLine <- highlighting.toLine
      toColumn <- highlighting.toColumn
    } yield {
      val from = doc.getLineStartOffset(highlighting.fromLine) + highlighting.fromColumn
      val to = doc.getLineStartOffset(toLine) + toColumn
      new TextRange(from, to)
    }
  }

  private def offset(highlighting: ExternalHighlighting, editor: Editor): Option[Int] = {
    val line = highlighting.fromLine - 1
    val column = (highlighting.fromColumn - 1).max(0)
    if (line < 0) {
      None
    } else {
      val lineTextFromMessage = lineText(highlighting.message)
      val document = editor.getDocument
      // TODO: dotc and scalac report different lines in their messages :(
      val actualLine =
        Seq(line, line - 1, line + 1)
          .find { lineNumber =>
            documentLine(document, lineNumber).contains(lineTextFromMessage)
          }
      actualLine.map(line => document.getLineStartOffset(line) + column)
    }
  }

  private def lineText(messageText: String): String = {
    val trimmed = messageText.trim
    val lastLineSeparator = trimmed.lastIndexOf('\n')
    if (lastLineSeparator > 0) trimmed.substring(lastLineSeparator).trim else ""
  }

  private def documentLine(document: Document, line: Int): Option[String] =
    if (line >= 0 && line < document.getLineCount) {
      val lineStart = document.getLineStartOffset(line)
      val lineEnd = document.getLineEndOffset(line)
      Some(document.getText(TextRange.create(lineStart, lineEnd)).trim)
    } else {
      None
    }
}
