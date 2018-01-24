package org.jetbrains.plugins.scala
package lang
package parser

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiUtilCore
import org.jetbrains.plugins.scala.lang.psi.impl.base.types._
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocElementType
import org.jetbrains.plugins.scala.lang.scaladoc.psi.ScalaDocPsiCreator

object ScalaPsiCreator extends ScalaPsiCreator

trait ScalaPsiCreator extends PsiCreator {
  override def createElement(node: ASTNode): PsiElement =
    createElement(node, node.getElementType)

  protected def createElement(node: ASTNode, elementType: IElementType): PsiElement = elementType match {
    case creator: SelfPsiCreator => creator.createElement(node)
    case _: ScalaDocElementType => ScalaDocPsiCreator.createElement(node)
    case _ => types(node)
  }

  protected def types(node: ASTNode): PsiElement = node.getElementType match {
    case ScalaElementTypes.SIMPLE_TYPE => new ScSimpleTypeElementImpl(node)
    case ScalaElementTypes.LITERAL_TYPE => new ScLiteralTypeElementImpl(node)
    case ScalaElementTypes.TUPLE_TYPE => new ScTupleTypeElementImpl(node)
    case ScalaElementTypes.TYPE => new ScFunctionalTypeElementImpl(node)
    case ScalaElementTypes.INFIX_TYPE => new ScInfixTypeElementImpl(node)
    case ScalaElementTypes.TYPE_ARGS => new ScTypeArgsImpl(node)
    case ScalaElementTypes.ANNOT_TYPE => new ScAnnotTypeElementImpl(node)
    case ScalaElementTypes.WILDCARD_TYPE => new ScWildcardTypeElementImpl(node)
    case ScalaElementTypes.TYPE_PROJECTION => new ScTypeProjectionImpl(node)
    case ScalaElementTypes.TYPE_GENERIC_CALL => new ScParameterizedTypeElementImpl(node)
    case ScalaElementTypes.TYPE_VARIABLE => new ScTypeVariableTypeElementImpl(node)
    case ScalaElementTypes.BLOCK_EXPR => PsiUtilCore.NULL_PSI_ELEMENT
    case _ => new ASTWrapperPsiElement(node)
  }

  trait SelfPsiCreator extends PsiCreator

}

trait PsiCreator {
  def createElement(node: ASTNode): PsiElement
}