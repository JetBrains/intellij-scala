package org.jetbrains.plugins.scala.lang.psi.impl.base.patterns

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScGivenPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.ScType

final class ScGivenPatternImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScPatternImpl with ScGivenPattern  {

  override def typeElement: ScTypeElement = findChildByClassScala(classOf[ScTypeElement])

  override def isIrrefutableFor(t: Option[ScType]): Boolean = {
    for {
      ty <- t
      Right(tp) <- Some(`type`())
    } yield {
      ty conforms tp
    }
  }.getOrElse(false)
}
