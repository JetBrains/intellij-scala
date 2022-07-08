package org.jetbrains.plugins.scala
package codeInsight
package template
package impl

import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

final class ScalaCodeContextType extends ScalaFileTemplateContextType.ElementContextType("CODE", ScalaCodeInsightBundle.message("element.context.type.code")) {

  override protected def isInContext(offset: Int)
                                    (implicit file: ScalaFile): Boolean =
    !(ScalaCommentContextType.isInContext(offset) ||
      ScalaStringContextType.isInContext(offset))
}