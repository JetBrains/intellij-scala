package org.jetbrains.plugins.dotty.lang.parser

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.dotty.lang.psi.impl.base.types.{DottyAndTypeElementImpl, DottyRefinedTypeElementImpl, DottyTypeArgumentNameElementImpl}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType

/**
  * @author adkozlov
  */
object DottyElementTypes {

  val REFINED_TYPE: ScalaElementType = new ScalaElementType("Dotty refined type") {
    override def createElement(node: ASTNode) = new DottyRefinedTypeElementImpl(node)
  }

  val WITH_TYPE: ScalaElementType = new ScalaElementType("Dotty with type") {
    override def createElement(node: ASTNode) = new DottyAndTypeElementImpl(node)
  }

  val TYPE_ARGUMENT_NAME: ScalaElementType = new ScalaElementType("Dotty type argument name") {
    override def createElement(node: ASTNode) = new DottyTypeArgumentNameElementImpl(node)
  }
}
