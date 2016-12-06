package org.jetbrains.plugins.dotty.lang.parser

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.{IElementType, TokenSet}
import org.jetbrains.plugins.dotty.lang.psi.impl.base.types._
import org.jetbrains.plugins.dotty.lang.psi.impl.toplevel.typedef.DottyTraitImpl
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes._
import org.jetbrains.plugins.scala.lang.parser.ScalaPsiCreator

/**
  * @author adkozlov
  */
object DottyPsiCreator extends ScalaPsiCreator {
  private val idTokenSet = TokenSet.create(REFERENCE, ScalaTokenTypes.tAND, ScalaTokenTypes.tOR)

  override protected def createElement(node: ASTNode, elementType: IElementType): PsiElement = elementType match {
    case TRAIT_DEFINITION => new DottyTraitImpl(node)
    case _ => super.createElement(node, elementType)
  }

  override protected def types(node: ASTNode): PsiElement = node.getElementType match {
    case TYPE => new DottyFunctionalTypeElementImpl(node)
    case ANNOT_TYPE => new DottyAnnotTypeElementImpl(node)
    case INFIX_TYPE => node.findChildByType(idTokenSet).getElementType match {
      case REFERENCE => new DottyInfixTypeElementImpl(node)
      case ScalaTokenTypes.tAND => new DottyAndTypeElementImpl(node)
      case ScalaTokenTypes.tOR => new DottyOrTypeElementImpl(node)
    }
    case SIMPLE_TYPE => new DottySimpleTypeElementImpl(node)
    case WILDCARD_TYPE => new DottyWildcardTypeElementImpl(node)
    case TYPE_GENERIC_CALL => new DottyParameterizedTypeElementImpl(node)
    case TUPLE_TYPE => new DottyTupleTypeElementImpl(node)
    case TYPE_ARGS => new DottyTypeArgsImpl(node)
    case _ => super.types(node)
  }
}
