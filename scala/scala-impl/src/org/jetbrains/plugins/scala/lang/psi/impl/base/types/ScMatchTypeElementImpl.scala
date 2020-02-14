package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{
  ScMatchTypeCases,
  ScMatchTypeElement,
  ScTypeElement
}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, TypeResult}

class ScMatchTypeElementImpl(node: ASTNode)
  extends ScalaPsiElementImpl(node)
    with ScMatchTypeElement {

  override def scrutineeTypeElement: ScTypeElement = findChildByClassScala(classOf[ScTypeElement])

  override def cases: Option[ScMatchTypeCases] = findChild(classOf[ScMatchTypeCases])

  override protected def innerType: TypeResult =
    Failure("Match types are not yet supported")
}
