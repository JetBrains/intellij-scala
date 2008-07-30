package org.jetbrains.plugins.scala.lang.psi.api.statements

import types.ScType
import psi.ScalaPsiElement
import toplevel.typedef._
import com.intellij.psi._
import base.types.ScTypeElement
import toplevel.ScTyped

/**
* @author Alexander Podkhalyuzin
* Date: 08.04.2008
*/

trait ScValue extends ScalaPsiElement with ScMember with ScDocCommentOwner with ScDeclaredElementsHolder {
  def typeElement = findChild(classOf[ScTypeElement])
  def getType : ScType
}