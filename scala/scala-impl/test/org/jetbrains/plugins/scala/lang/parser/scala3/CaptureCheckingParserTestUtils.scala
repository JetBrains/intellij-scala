package org.jetbrains.plugins.scala.lang.parser.scala3

import com.intellij.psi.PsiElement
import com.intellij.psi.tree.{IElementType, TokenSet}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes.tIDENTIFIER
import org.junit.Assert.assertTrue

/** Raw tree navigation for recovery matrices, independent of Scala PSI accessors. */
private[scala3] object CaptureCheckingParserTestUtils {
  def descendantsOfType(root: PsiElement, elementType: IElementType): Seq[PsiElement] =
    root.depthFirst().filter(_.getNode.getElementType == elementType).toSeq

  def childrenOfType(root: PsiElement, elementType: IElementType): Seq[PsiElement] =
    root.getNode.getChildren(TokenSet.create(elementType)).map(_.getPsi).toSeq

  def identifierText(element: PsiElement): String = {
    // Variance signs also use identifier tokens; the name is the last direct identifier.
    val identifiers = element.getNode.getChildren(TokenSet.create(tIDENTIFIER))
    assertTrue(element.getText, identifiers.nonEmpty)
    identifiers.last.getText
  }
}
