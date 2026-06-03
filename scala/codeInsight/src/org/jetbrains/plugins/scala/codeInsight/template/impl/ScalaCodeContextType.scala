package org.jetbrains.plugins.scala.codeInsight.template.impl

import com.intellij.codeInsight.template.TemplateActionContext
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

final class ScalaCodeContextType
  extends ScalaFileTemplateContextType.ElementContextType(ScalaCodeInsightBundle.message("element.context.type.code")) {

  protected def isInContextInScalaFile(context: TemplateActionContext)(implicit file: ScalaFile): Boolean = {
    !(ScalaCommentContextType.isInContext(context) ||
      ScalaStringContextType.isInContext(context))
  }
}
