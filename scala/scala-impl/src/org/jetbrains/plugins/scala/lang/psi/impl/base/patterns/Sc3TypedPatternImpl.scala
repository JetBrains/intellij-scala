package org.jetbrains.plugins.scala.lang.psi.impl.base.patterns

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{Sc3TypedPattern, ScPattern, ScTypePattern}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

final class Sc3TypedPatternImpl(node: ASTNode)
    extends ScalaPsiElementImpl(node)
    with ScPatternImpl
    with Sc3TypedPattern
    with TypedPatternLikeImpl {

  override def pattern: ScPattern                 = findChild[ScPattern].get
  override def typePattern: Option[ScTypePattern] = findChild[ScTypePattern]

  override def `type`(): TypeResult = {
    for {
      innerPatternType <- pattern.`type`()
      typeElementType  <- this.flatMapType(typePattern.map(_.typeElement))
    } yield innerPatternType.glb(typeElementType)
  }

  override def toString: String = "Scala3 TypedPattern"
}
