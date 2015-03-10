package org.jetbrains.plugins.scala.codeInsight.template.impl

import com.intellij.codeInsight.template.TemplateContextType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiComment, PsiFile, PsiWhiteSpace}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

/**
 * @author Alefas
 * @since 18/12/14.
 */
class ScalaCommentContextType extends TemplateContextType("SCALA_COMMENT", "Comment", classOf[ScalaLiveTemplateContextType]) {
  override def isInContext(file: PsiFile, offset: Int): Boolean =
    ScalaCommentContextType.isInContext(file, offset)
}

object ScalaCommentContextType {
  def isInContext(file: PsiFile, offset: Int): Boolean = {
    if (!file.isInstanceOf[ScalaFile]) return false
    val element = file.findElementAt(offset) match {
      case elem: PsiWhiteSpace if offset > 0 => file.findElementAt(offset - 1)
      case elem => elem
    }
    val comment = PsiTreeUtil.getParentOfType(element, classOf[PsiComment], false)
    comment != null
  }
}