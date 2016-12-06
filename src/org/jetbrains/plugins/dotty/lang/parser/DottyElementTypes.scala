package org.jetbrains.plugins.dotty.lang.parser

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.dotty.lang.psi.impl.base.types.{DottyAndTypeElementImpl, DottyRefinedTypeElementImpl, DottyTypeArgumentNameElementImpl}
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaPsiCreator.SelfPsiCreator

/**
  * @author adkozlov
  */
object DottyElementTypes {

  val REFINED_TYPE = new ScalaElementType("Dotty refined type") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new DottyRefinedTypeElementImpl(node)
  }
  val WITH_TYPE = new ScalaElementType("Dotty with type") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new DottyAndTypeElementImpl(node)
  }
  val TYPE_ARGUMENT_NAME = new ScalaElementType("Dotty type argument name") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new DottyTypeArgumentNameElementImpl(node)
  }
}
