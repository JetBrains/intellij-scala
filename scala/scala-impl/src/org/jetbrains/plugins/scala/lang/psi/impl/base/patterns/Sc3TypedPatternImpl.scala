package org.jetbrains.plugins.scala.lang.psi.impl.base.patterns

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{Sc3TypedPattern, ScPattern, ScTypePattern}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl

final class Sc3TypedPatternImpl(node: ASTNode)
  extends ScalaPsiElementImpl(node)
    with ScPatternImpl
    with Sc3TypedPattern
    with TypedPatternLikeImpl
{
  override def pattern: Option[ScPattern] = findChild(classOf[ScPattern])
  override def typePattern: Option[ScTypePattern] = findChild(classOf[ScTypePattern])

  override def toString: String = "Scala3 TypedPattern"
}