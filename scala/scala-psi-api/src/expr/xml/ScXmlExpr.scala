package org.jetbrains.plugins.scala.lang.psi.api.expr
package xml

import com.intellij.psi.PsiElement

trait ScXmlExpr extends ScExpression {
  def getElements: Seq[PsiElement] = getChildren.filter {
    case _: ScXmlElement | _: ScXmlPI | _: ScXmlCDSect | _: ScXmlComment => true
    case _ => false
  }.toSeq
}