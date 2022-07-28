package org.jetbrains.plugins.scala
package codeInsight
package template
package impl

import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle
import org.jetbrains.plugins.scala.lang.psi.api._

final class ScalaStringContextType extends ScalaFileTemplateContextType.ElementContextType("STRING", ScalaCodeInsightBundle.message("element.context.type.string")) {

  override protected def isInContext(offset: Int)
                                    (implicit file: ScalaFile): Boolean =
    ScalaStringContextType.isInContext(offset)
}

object ScalaStringContextType {

  private[impl] def isInContext(offset: Int)
                               (implicit file: ScalaFile): Boolean =
    ScalaFileTemplateContextType.isInContext(offset, classOf[base.ScLiteral])(_.isString)
}