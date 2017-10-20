package org.jetbrains.plugins.dotty.lang.psi.api.base.types

import org.jetbrains.plugins.dotty.lang.psi.impl.DottyPsiElementFactory.createTypeElementFromText
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScDesugarizableTypeElement, ScTypeElement}

/**
  * @author adkozlov
  */
trait DottyDesugarizableTypeElement extends ScDesugarizableTypeElement {
  override def typeElementFromText: (String) => ScTypeElement = createTypeElementFromText(_, getContext, this)
}
