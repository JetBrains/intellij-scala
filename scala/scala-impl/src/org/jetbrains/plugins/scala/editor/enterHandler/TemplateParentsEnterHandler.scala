package org.jetbrains.plugins.scala.editor.enterHandler

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.Result
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.util.Ref
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFile, PsiWhiteSpace}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.editor.enterHandler.TemplateParentsEnterHandler._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScTemplateBody, ScTemplateParents}

/**
 * Handle enter after last `with` in extends list e.g. {{{
 *   class A extends B
 *     with C
 *     with D<caret>
 * }}}
 *
 * (see tests in the commit for various edge cases)
 */
class TemplateParentsEnterHandler extends EnterHandlerDelegateAdapter {

  override def preprocessEnter(
    file: PsiFile,
    editor: Editor,
    caretOffsetRef: Ref[Integer],
    caretAdvance: Ref[Integer],
    dataContext: DataContext,
    originalHandler: EditorActionHandler
  ): Result = {
    if (!file.is[ScalaFile])
      return Result.Continue

    val caretOffset = caretOffsetRef.get.intValue

    // TODO: we do so many `findElementAt` in different handlers and each time it does the tr traversal from the root
    //  can we somehow reuse this value in our handlers (our in the platform) if no document/file modifications were made?
    val elementAtCaret = file.findElementAt(caretOffset)

    if (elementAtCaret != null && isBetweenParentsAndTemplateBody(elementAtCaret))
      Result.DefaultSkipIndent
    else if (isAfterParentsWithoutTemplateBody(elementAtCaret, caretOffset, file, editor.getDocument))
      Result.DefaultSkipIndent
    else
      Result.Continue
  }
}

object TemplateParentsEnterHandler {

  private def isBetweenParentsAndTemplateBody(elementAtCaret: PsiElement): Boolean = elementAtCaret match {
    case (_: PsiWhiteSpace) && PrevSibling(_: ScTemplateParents) =>
      true
    case (_: LeafPsiElement)
      && ElementType(ScalaTokenTypes.tLBRACE | ScalaTokenTypes.tCOLON)
      && Parent((_: ScTemplateBody) && PrevSiblingNotWhitespace(_: ScTemplateParents)) =>
      true
    case _ =>
      false
  }

  private def isAfterParentsWithoutTemplateBody(@Nullable elementAtCaret: PsiElement, caretOffset: Int, file: PsiFile, document: Document): Boolean =
    elementAtCaret match {
      case ws: PsiWhiteSpace =>
        // optimisation, handle cases when we press Enter on the same line with `with`
        if (hasContentBeforeOnSameLine(caretOffset, document.getCharsSequence)) {
          PsiTreeUtil.prevCodeLeaf(ws).nullSafe.exists { prevLeaf =>
            val prevLeafEndOffset = prevLeaf.endOffset
            val parents = prevLeaf.parentsInFile.takeWhile(_.endOffset <= prevLeafEndOffset): Iterator[PsiElement]
            parents.exists(_.is[ScTemplateParents])
          }
        }
        else false
      case null if caretOffset == document.getTextLength =>
        file.pathToLastChild.exists(_.is[ScTemplateParents])
      case _ =>
        false
    }

  private def hasContentBeforeOnSameLine(caretOffset: Int, text: CharSequence): Boolean = {
    var idx = caretOffset - 1
    while (idx >= 0) {
      val ch = text.charAt(idx)

      if (ch == '\n')
        return false
      else if (!ch.isWhitespace)
        return true
      else
        idx -= 1
    }
    false
  }
}
