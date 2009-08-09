package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr
package xml

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/**
* @author Alexander Podkhalyuzin
* Date: 21.04.2008
*/

trait ScXmlExpr extends ScExpression {
  def getElements: Seq[PsiElement] = getChildren.filter(_ match {
    case _: ScXmlElement | _: ScXmlPI | _: ScXmlCDSect | _: ScXmlComment  => true
    case _ => false
  })
}