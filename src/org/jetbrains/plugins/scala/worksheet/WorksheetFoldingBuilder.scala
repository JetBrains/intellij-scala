package org.jetbrains.plugins.scala
package worksheet

import java.lang.String
import com.intellij.lang.folding.{FoldingBuilder, FoldingDescriptor}
import com.intellij.openapi.editor.Document
import com.intellij.psi._
import impl.source.SourceTreeToPsiMap
import com.intellij.lang.ASTNode
import collection.mutable.ArrayBuffer
import java.util
import lang.lexer.ScalaTokenTypes
import com.intellij.openapi.util.TextRange
import util.Collections

/**
 * @author Ksenia.Sautina
 * @since 10/23/12
 */

class WorksheetFoldingBuilder extends FoldingBuilder {

  def getPlaceholderText(node: ASTNode): String = {
    val element: PsiElement = SourceTreeToPsiMap.treeElementToPsi(node)
    element match {
      case comment: PsiComment =>
        val text = comment.getText
        if (text.startsWith(WorksheetFoldingBuilder.FIRST_LINE_PREFIX)) {
          return WorksheetFoldingBuilder.FIRST_LINE_PREFIX
        } else if (text.startsWith(WorksheetFoldingBuilder.LINE_PREFIX)) {
          return WorksheetFoldingBuilder.LINE_PREFIX
        }
      case _ =>
    }
    "/../"
  }

  def isCollapsedByDefault(node: ASTNode): Boolean = {
    true
  }

  override def buildFoldRegions(astNode: ASTNode, document: Document): Array[FoldingDescriptor] = {
    val descriptors = new ArrayBuffer[FoldingDescriptor]
    val processedComments = new util.HashSet[PsiElement]
    appendDescriptors(astNode, document, descriptors, processedComments)
    descriptors.toArray
  }

  private def appendDescriptors(node: ASTNode,
                                document: Document,
                                descriptors: ArrayBuffer[FoldingDescriptor],
                                processedComments: util.HashSet[PsiElement]) {
    if (node.getElementType == ScalaTokenTypes.tLINE_COMMENT &&
      (node.getText.startsWith(WorksheetFoldingBuilder.FIRST_LINE_PREFIX) ||
        node.getText.startsWith(WorksheetFoldingBuilder.LINE_PREFIX))) {
      val length = Math.max(WorksheetFoldingBuilder.FIRST_LINE_PREFIX.length, WorksheetFoldingBuilder.LINE_PREFIX.length)
      descriptors += (new FoldingDescriptor(node,
        new TextRange(node.getPsi.asInstanceOf[PsiComment].getTextRange.getStartOffset,
          node.getPsi.asInstanceOf[PsiComment].getTextRange.getStartOffset + length), null, Collections.emptySet[AnyRef], true))
    }

    for (child <- node.getChildren(null)) {
      appendDescriptors(child, document, descriptors, processedComments)
    }
  }
}

object WorksheetFoldingBuilder {
  val FIRST_LINE_PREFIX = ">"
  val LINE_PREFIX = " "
}
