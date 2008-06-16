package org.jetbrains.plugins.scala.lang.psi.api.statements

import psi.ScalaPsiElement
import toplevel.typedef._
import com.intellij.psi._
import base.types.ScTypeElement

/**
* @author Alexander Podkhalyuzin
* Date: 08.04.2008
*/

trait ScValue extends ScalaPsiElement with ScMember {

  /**
  * @return all binded identifiers
  */
  def ids: Seq[PsiElement]

  def typeElement = findChild(classOf[ScTypeElement])
}