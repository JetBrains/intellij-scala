package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr
package xml

import org.jetbrains.plugins.scala.lang.psi.api._


import com.intellij.psi.PsiElement

/**
* @author Alexander Podkhalyuzin
* Date: 21.04.2008
*/

trait ScXmlExprBase extends ScExpressionBase { this: ScXmlExpr =>
  def getElements: Seq[PsiElement] = getChildren.filter {
    case _: ScXmlElement | _: ScXmlPI | _: ScXmlCDSect | _: ScXmlComment => true
    case _ => false
  }.toSeq
}