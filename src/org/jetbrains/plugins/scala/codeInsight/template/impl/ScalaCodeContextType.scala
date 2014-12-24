package org.jetbrains.plugins.scala.codeInsight.template.impl

import com.intellij.codeInsight.template.TemplateContextType
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

/**
 * @author Alefas
 * @since 18/12/14.
 */
class ScalaCodeContextType extends TemplateContextType("SCALA_CODE", "Code", classOf[ScalaLiveTemplateContextType]) {
  def isInContext(file: PsiFile, offset: Int): Boolean = {
    if (!file.isInstanceOf[ScalaFile]) return false
    !ScalaCommentContextType.isInContext(file, offset) && !ScalaStringContextType.isInContext(file, offset)
  }
}