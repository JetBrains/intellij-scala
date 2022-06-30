package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScTypePattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScMatchTypeCase, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl

class ScMatchTypeCaseImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScMatchTypeCase {

  override def pattern: Option[ScTypePattern] = findChild[ScTypePattern]

  override def result: Option[ScTypeElement] = findLastChild[ScTypeElement]
}
