package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi.PsiElement

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScSelfInvocation extends ScalaPsiElement {
  def args: Option[ScArgumentExprList] = findChild(classOf[ScArgumentExprList])

  def bind: Option[PsiElement]

  def thisElement: PsiElement = getFirstChild
}