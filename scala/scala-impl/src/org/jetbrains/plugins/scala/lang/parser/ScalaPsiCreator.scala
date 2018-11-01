package org.jetbrains.plugins.scala
package lang
package parser

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilCore
import org.jetbrains.plugins.scala.lang.psi.impl.base.types._

object ScalaPsiCreator extends PsiCreator {

  import ScalaElementTypes._

  def createElement(node: ASTNode): PsiElement = node.getElementType match {
    case creator: SelfPsiCreator => creator.createElement(node)
    case _: scaladoc.lexer.ScalaDocElementType => scaladoc.psi.ScalaDocPsiCreator.createElement(node)
    case SIMPLE_TYPE => new ScSimpleTypeElementImpl(node)
    case LITERAL_TYPE => new ScLiteralTypeElementImpl(node)
    case TUPLE_TYPE => new ScTupleTypeElementImpl(node)
    case TYPE => new ScFunctionalTypeElementImpl(node)
    case INFIX_TYPE => new ScInfixTypeElementImpl(node)
    case TYPE_ARGS => new ScTypeArgsImpl(node)
    case ANNOT_TYPE => new ScAnnotTypeElementImpl(node)
    case WILDCARD_TYPE => new ScWildcardTypeElementImpl(node)
    case TYPE_PROJECTION => new ScTypeProjectionImpl(node)
    case TYPE_GENERIC_CALL => new ScParameterizedTypeElementImpl(node)
    case TYPE_VARIABLE => new ScTypeVariableTypeElementImpl(node)
    case BLOCK_EXPR => PsiUtilCore.NULL_PSI_ELEMENT
    case _ => new ASTWrapperPsiElement(node)
  }

  trait SelfPsiCreator extends PsiCreator
}

trait PsiCreator {
  def createElement(node: ASTNode): PsiElement
}