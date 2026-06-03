package org.jetbrains.plugins.scala.editor.enterHandler

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.Result
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.{Ref, TextRange}
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.editor.{DocumentExt, indentKeyword}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScCatchBlock, ScFinallyBlock, ScIf}

final class FormatKeywordAfterEnterHandler extends EnterHandlerDelegateAdapter {
  type KeywordF = PsiFile => (Document, Project, PsiElement, Int) => Unit

  private val keywordsF: Map[String, KeywordF] = Map(
    ("else", indentKeyword[ScIf](ScalaTokenTypes.kELSE, _)),
    ("atch", indentKeyword[ScCatchBlock](ScalaTokenTypes.kCATCH, _)),
    ("ally", indentKeyword[ScFinallyBlock](ScalaTokenTypes.kFINALLY, _)),
  )

  private var selectedKeywordF: Option[KeywordF] = None

  override def preprocessEnter(file: PsiFile, editor: Editor,
                               caretOffset: Ref[Integer], caretAdvance: Ref[Integer],
                               dataContext: DataContext,
                               originalHandler: EditorActionHandler): Result = {
    val offset = caretOffset.get()

    val textBeforeCaret =
      if (offset > 4) Some(editor.getDocument.getText(TextRange.from(offset - 4, 4)))
      else None

    selectedKeywordF = textBeforeCaret.flatMap(keywordsF.get)

    Result.Continue
  }

  override def postProcessEnter(file: PsiFile, editor: Editor, dataContext: DataContext): Result = {

    selectedKeywordF.foreach { keywordF =>
      val project = file.getProject
      val document = editor.getDocument
      document.commit(project)
      val caretOffset = editor.getCaretModel.getOffset
      val element = file.findElementAt(caretOffset)

      if (element != null) {
        keywordF(file)(document, project, element, caretOffset)
      }
    }

    Result.Continue
  }
}
