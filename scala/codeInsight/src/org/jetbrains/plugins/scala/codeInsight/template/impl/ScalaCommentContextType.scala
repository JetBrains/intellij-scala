package org.jetbrains.plugins.scala.codeInsight.template.impl

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.psi._
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

final class ScalaCommentContextType
  extends ScalaFileTemplateContextType.ElementContextType(ScalaCodeInsightBundle.message("element.context.type.comment")) {

  protected def isInContextInScalaFile(context: TemplateActionContext)(implicit file: ScalaFile): Boolean =
    ScalaCommentContextType.isInContext(context)
}

object ScalaCommentContextType {

  private[impl] def isInContext(context: TemplateActionContext)
                               (implicit file: ScalaFile): Boolean = {
    val offset = context.getStartOffset
    val elementAtOffset = file.findElementAt(offset) match {
      case _: PsiWhiteSpace if offset > 0 => file.findElementAt(offset - 1)
      case element => element
    }

    util.PsiTreeUtil.getNonStrictParentOfType(elementAtOffset, classOf[PsiComment]) != null
  }
}
