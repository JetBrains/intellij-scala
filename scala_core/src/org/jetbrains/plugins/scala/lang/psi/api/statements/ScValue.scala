package org.jetbrains.plugins.scala.lang.psi.api.statements

import psi.ScalaPsiElement
import toplevel.typedef._
import com.intellij.psi._
import base.types.ScTypeElement
import toplevel.ScTyped

/**
* @author Alexander Podkhalyuzin
* Date: 08.04.2008
*/

trait ScValue extends ScalaPsiElement with ScMember {
  def declaredElements: Seq[ScTyped]

  def typeElement = findChild(classOf[ScTypeElement])
}