package org.jetbrains.plugins.dotty.lang.psi.impl.base.types

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.impl.base.types.ScSimpleTypeElementImpl

/**
  * @author adkozlov
  */
class DottySimpleTypeElementImpl(node: ASTNode) extends ScSimpleTypeElementImpl(node) {
  // TODO: rewrite
  //  override protected def innerType(context: TypingContext) = this.success(reference.collect {
  //    case ref if ref.getText == "Unit" => Unit
  //  }.getOrElse(Any))
}
