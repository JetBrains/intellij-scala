package org.jetbrains.plugins.scala.codeInsight.template.impl

import com.intellij.codeInsight.template.TemplateActionContext
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle
import org.jetbrains.plugins.scala.lang.psi.api._

final class ScalaXmlContextType
  extends ScalaFileTemplateContextType.ElementContextType(ScalaCodeInsightBundle.message("element.context.type.xml")) {

  protected def isInContextInScalaFile(context: TemplateActionContext)(implicit file: ScalaFile): Boolean =
    ScalaFileTemplateContextType.isInContext(context, classOf[expr.xml.ScXmlExpr])()
}
