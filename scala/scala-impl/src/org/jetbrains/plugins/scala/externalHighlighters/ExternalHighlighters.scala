package org.jetbrains.plugins.scala.externalHighlighters

import java.util.Collections

import com.intellij.codeInsight.daemon.impl.{HighlightInfo, HighlightInfoType, UpdateHighlightersUtil}
import com.intellij.openapi.editor.{Document, Editor, EditorFactory}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.psi.{JavaTokenType, PsiElement, PsiFile, PsiJavaToken, PsiManager, PsiWhiteSpace}
import org.jetbrains.plugins.scala.annotator.ScalaHighlightingMode
import org.jetbrains.plugins.scala.editor.DocumentExt
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, invokeLater}
import org.jetbrains.plugins.scala.externalHighlighters.ExternalHighlighting.Pos
import org.jetbrains.plugins.scala.settings.ProblemSolverUtils
import org.jetbrains.plugins.scala.extensions.inReadAction

import scala.collection.JavaConverters._

object ExternalHighlighters {

  // A random number of highlighters group to avoid conflicts with standard groups.
  private final val ScalaCompilerPassId = 979132998
  
  def applyHighlighting(project: Project,
                        editor: Editor,
                        state: HighlightingState): Unit =
    if (ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project)) {
      val document = editor.getDocument
      for {
        virtualFile <- document.virtualFile
        psiFile <- Option(inReadAction(PsiManager.getInstance(project).findFile(virtualFile)))
        if ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(psiFile)
      } invokeLater {
        val externalHighlights = state.getOrElse(virtualFile, Set.empty)
        val highlightInfos = externalHighlights.flatMap(toHighlightInfo(_, editor))
        UpdateHighlightersUtil.setHighlightersToEditor(
          project,
          document, 0, document.getTextLength,
          highlightInfos.toSeq.asJava,
          editor.getColorsScheme,
          ScalaCompilerPassId
        )
      }
    }
  
  def eraseAllHighlightings(project: Project): Unit = {
    for {
      editor <- EditorFactory.getInstance.getAllEditors
      editorProject <- Option(editor.getProject)
      if editorProject == project
    } invokeLater {
      val document = editor.getDocument
      UpdateHighlightersUtil.setHighlightersToEditor(
        project,
        document, 0, document.getTextLength,
        Collections.emptyList(),
        editor.getColorsScheme,
        ScalaCompilerPassId
      )
    }
    ProblemSolverUtils.clearAllProblemsFromExternalSource(project, this)
  }

  def informWolf(project: Project, state: HighlightingState): Unit =
    if (ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project)) {
      val errorTypes = Set(HighlightInfoType.ERROR, HighlightInfoType.WRONG_REF)
      ProblemSolverUtils.clearAllProblemsFromExternalSource(project, this)
      val wolf = WolfTheProblemSolver.getInstance(project)
      val errorFiles = state.collect {
        case (file, fileState) if fileState.exists(errorTypes contains _.highlightType) => file
      }
      errorFiles.foreach(wolf.reportProblemsFromExternalSource(_, this))
    }

  private def toHighlightInfo(highlighting: ExternalHighlighting, editor: Editor): Option[HighlightInfo] = {
    val message = highlighting.message
    for {
      startOffset <- convertToOffset(highlighting.from, message, editor)
      highlightRange <- calculateRangeToHighlight(startOffset, highlighting.to, message, editor)
      description = message.trim.stripSuffix(lineText(message))
    } yield HighlightInfo
      .newHighlightInfo(highlighting.highlightType)
      .range(highlightRange)
      .descriptionAndTooltip(description)
      .group(ScalaCompilerPassId)
      .create()
  }

  private def calculateRangeToHighlight(startOffset: Int,
                                        to: Pos,
                                        message: String,
                                        editor: Editor): Option[TextRange] =
    convertToOffset(to, message, editor)
      .filter(_ != startOffset)
      .map { endOffset => TextRange.create(startOffset, endOffset) }
      .orElse(guessRangeToHighlight(editor, startOffset))

  private def guessRangeToHighlight(editor: Editor, startOffset: Int): Option[TextRange] =
    for {
      virtualFile <- editor.getDocument.virtualFile
      psiFile <- Option(PsiManager.getInstance(editor.getProject).findFile(virtualFile))
      element <- elementToHighlight(psiFile, startOffset)
    } yield element.getTextRange

  private def elementToHighlight(file: PsiFile, offset: Int): Option[PsiElement] =
    Option(file.findElementAt(offset)).flatMap {
      case whiteSpace: PsiWhiteSpace =>
        whiteSpace.prevElementNotWhitespace
      case javaToken: PsiJavaToken if javaToken.getTokenType == JavaTokenType.DOT =>
        javaToken.nextElementNotWhitespace
      case other =>
        Some(other)
    }

  private def convertToOffset(pos: Pos,
                              message: String,
                              editor: Editor): Option[Int] = pos match {
    case Pos.LineColumn(l, c) =>
      val line = l - 1
      val column = (c - 1).max(0)
      if (line < 0) {
        None
      } else {
        val lineTextFromMessage = lineText(message)
        val document = editor.getDocument
        // TODO: dotc and scalac report different lines in their messages :(
        val actualLine =
          Seq(line, line - 1, line + 1)
            .find { lineNumber =>
              documentLine(document, lineNumber).contains(lineTextFromMessage)
            }
        actualLine.map(line => document.getLineStartOffset(line) + column)
      }
    case Pos.Offset(offset) =>
      Some(offset)
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
