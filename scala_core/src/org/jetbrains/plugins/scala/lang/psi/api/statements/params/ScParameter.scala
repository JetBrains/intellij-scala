package org.jetbrains.plugins.scala.lang.psi.api.statements.params

import icons.Icons
import javax.swing.Icon
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi._
import api.statements.params._
import types.ScType
import api.toplevel.ScTyped

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScParameter extends ScNamedElement with ScTyped with PsiParameter {

  def getTypeElement: PsiTypeElement

  def typeElement: Option[ScTypeElement]

  def paramType: Option[ScParameterType]

  override def getTextOffset: Int = nameId.getTextRange.getStartOffset

  //todo implement me!
  def hasModifierProperty(p: String) = false

  override def getIcon(flags: Int): Icon = Icons.PARAMETER

}