package org.jetbrains.plugins.scala
package codeInsight
package template
package impl

import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle
import org.jetbrains.plugins.scala.lang.psi.api._

/**
  * Nikolay.Tropin
  * 1/20/14
  */
final class ScalaXmlContextType extends ScalaFileTemplateContextType.ElementContextType("XML", ScalaCodeInsightBundle.message("element.context.type.xml")) {

  override protected def isInContext(offset: Int)
                                    (implicit file: ScalaFile): Boolean =
    ScalaFileTemplateContextType.isInContext(offset, classOf[expr.xml.ScXmlExpr])()
}
