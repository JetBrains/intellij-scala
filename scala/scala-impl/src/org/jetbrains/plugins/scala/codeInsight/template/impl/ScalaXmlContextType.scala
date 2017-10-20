package org.jetbrains.plugins.scala
package codeInsight.template.impl

import com.intellij.codeInsight.template.TemplateContextType
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml.ScXmlExpr

/**
 * Nikolay.Tropin
 * 1/20/14
 */
class ScalaXmlContextType extends TemplateContextType("SCALA_XML", "XML", classOf[ScalaLiveTemplateContextType]) {
  def isInContext(file: PsiFile, offset: Int): Boolean = {
    val element = file.findElementAt(offset)
    PsiTreeUtil.getParentOfType(element, classOf[ScXmlExpr]) != null
  }
}
