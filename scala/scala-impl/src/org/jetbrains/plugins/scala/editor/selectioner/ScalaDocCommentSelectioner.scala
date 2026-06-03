// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.scala.editor.selectioner

import com.intellij.codeInsight.editorActions.wordSelection.LineCommentSelectioner
import com.intellij.lang.ASTNode
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes

import java.util

/**
 * This is an analog of JavaDoc version [[com.intellij.codeInsight.editorActions.wordSelection.DocCommentSelectioner]].
 * We also extends [[LineCommentSelectioner]], that automatically allows us
 * selecting of the whole doc comment without definition to which the doc is attached
 *
 * @see [[com.intellij.codeInsight.editorActions.wordSelection.BlockCommentSelectioner]] (works for Scala automatically)
 * @see [[com.intellij.codeInsight.editorActions.wordSelection.LineCommentSelectioner]] (works for Scala automatically)
 */
final class ScalaDocCommentSelectioner extends LineCommentSelectioner {
  override def canSelect(e: PsiElement): Boolean = {
    val node = e.getNode
    node != null && {
      node.getElementType == ScalaDocElementTypes.SCALA_DOC_COMMENT
    }
  }

  override def select(element: PsiElement, editorText: CharSequence, cursorOffset: Int, editor: Editor): util.List[TextRange] = {
    val node = element.getNode
    if (node == null)
      return null

    val result = super.select(element, editorText, cursorOffset, editor)
    if (result == null)
      return null

    val mainContentRange = contentParagraphsRange(node)
    mainContentRange.foreach(result.add)

    result
  }

  private def contentParagraphsRange(node: ASTNode): Option[TextRange] = {
    val children = node.getChildren(null)
    for {
      firstParagraph <- children.find(_.getElementType == ScalaDocElementTypes.DOC_PARAGRAPH)
      lastParagraph <- children.findLast(_.getElementType == ScalaDocElementTypes.DOC_PARAGRAPH)
    } yield new TextRange(firstParagraph.getStartOffset, lastParagraph.getTextRange.getEndOffset)
  }
}