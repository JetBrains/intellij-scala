package org.jetbrains.plugins.scala.lang.psi.impl.base.patterns

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScGivenPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl

final class ScGivenPatternImpl(node: ASTNode)
  extends ScalaPsiElementImpl(node)
    with ScPatternImpl
    with ScGivenPattern
    with TypedPatternLikeImpl
{
  override def typeElement: ScTypeElement = findChildByClassScala(classOf[ScTypeElement])
}
