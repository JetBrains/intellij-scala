package org.jetbrains.plugins.scala
package worksheet

import java.util
import java.util.Collections

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.{FoldingBuilder, FoldingDescriptor}
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import com.intellij.psi.impl.source.SourceTreeToPsiMap
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes

import scala.collection.mutable.ArrayBuffer

/**
 * @author Ksenia.Sautina
 * @since 10/23/12
 */

class WorksheetFoldingBuilder extends FoldingBuilder {

  override def getPlaceholderText(node: ASTNode): String = {
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

  override def isCollapsedByDefault(node: ASTNode): Boolean = {
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
                                processedComments: util.HashSet[PsiElement]): Unit = {
    if (node.getElementType == ScalaTokenTypes.tLINE_COMMENT &&
      (node.getText.startsWith(WorksheetFoldingBuilder.FIRST_LINE_PREFIX) ||
        node.getText.startsWith(WorksheetFoldingBuilder.LINE_PREFIX))) {
      val length = Math.max(WorksheetFoldingBuilder.FIRST_LINE_PREFIX.length, WorksheetFoldingBuilder.LINE_PREFIX.length)
      descriptors += new FoldingDescriptor(node,
        new TextRange(node.getPsi.asInstanceOf[PsiComment].getTextRange.getStartOffset,
          node.getPsi.asInstanceOf[PsiComment].getTextRange.getStartOffset + length), null, Collections.emptySet[AnyRef], true)
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
