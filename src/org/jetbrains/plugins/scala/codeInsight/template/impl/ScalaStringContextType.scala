package org.jetbrains.plugins.scala.codeInsight.template.impl

import com.intellij.codeInsight.template.TemplateContextType
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral

/**
 * @author Alefas
 * @since 18/12/14.
 */
class ScalaStringContextType extends TemplateContextType("SCALA_STRING", "String", classOf[ScalaLiveTemplateContextType]) {
  override def isInContext(file: PsiFile, offset: Int): Boolean =
    ScalaStringContextType.isInContext(file, offset)
}

object ScalaStringContextType {
  def isInContext(file: PsiFile, offset: Int): Boolean = {
    if (!file.isInstanceOf[ScalaFile]) return false
    val element = file.findElementAt(offset)
    PsiTreeUtil.getParentOfType(element, classOf[ScLiteral]) match {
      case literal: ScLiteral =>
        literal.isString
      case _ => false
    }
  }
}