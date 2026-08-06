package org.jetbrains.plugins.scala
package codeInsight
package template
package impl

import com.intellij.codeInsight.template.impl.TemplatePreprocessor
import com.intellij.openapi.editor.Editor
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes

import scala.annotation.tailrec

final class ScalaTemplatePreprocessor extends TemplatePreprocessor {

  import ScalaTemplatePreprocessor._

  override def preprocessTemplate(editor: Editor, file: PsiFile, caretOffset: Int,
                                  textToInsert: String, templateText: String): Unit =
    if (textToInsert.startsWith(Def + " ")) {
      for {
        element <- Option(file.findElementAt(caretOffset))

        leaf <- findNonEmptySibling(element)
        if leaf.getElementType == ScalaTokenTypes.kDEF
      } removeRedundantToken(leaf.getStartOffset, caretOffset)(editor)
    }
}

object ScalaTemplatePreprocessor {

  private val Def = "def"

  @tailrec
  private def findNonEmptySibling(element: PsiElement): Option[LeafPsiElement] =
    element.getPrevSiblingNotWhitespace match {
      case sibling: PsiElement if sibling.getTextLength == 0 => findNonEmptySibling(sibling)
      case sibling: LeafPsiElement => Some(sibling)
      case _ => None
    }

  private def removeRedundantToken(startOffset: Int, endOffset: Int)
                                  (implicit editor: Editor): Unit = editor.getDocument match {
    //first, make sure that the 'def' is on the same line (i.e. it is a redundant 'def' indeed and not a part of other unfinished code)
    case document if document.getLineNumber(startOffset) == document.getLineNumber(endOffset) =>
      //get rid of extra 'def' when expanding 'main' template (any other templates with 'def' will get affected too)
      document.deleteString(startOffset, endOffset)
      editor.getCaretModel.moveToOffset(startOffset)
    case _ =>
  }
}