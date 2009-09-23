package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import expr.{ScBlock, ScBlockStatement}
import icons.Icons
import javax.swing.Icon
import toplevel.templates.ScExtendsBlock
import toplevel.{ScTyped, ScTypeParametersOwner}
import types.ScType
import psi.ScalaPsiElement
import toplevel.typedef._
import com.intellij.psi._
import base.types.ScTypeElement
/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:45:29
*/

trait ScVariable extends ScBlockStatement with ScMember with ScDocCommentOwner with ScDeclaredElementsHolder with ScAnnotationsHolder {
  def declaredElements : Seq[ScTyped]
  def typeElement: Option[ScTypeElement]

  def declaredType: Option[ScType] = typeElement match {
    case Some(te) => Some(te.cachedType)
    case None => None
  }

  def getType : ScType

  override def getIcon(flags: Int): Icon = {
    import Icons._
    var parent = getParent
    while (parent != null) {
      parent match {
        case _: ScExtendsBlock => return FIELD_VAR
        case _: ScBlock => return VAR
        case _ => parent = parent.getParent
      }
    }
    null
  }
  


}