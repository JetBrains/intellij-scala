package org.jetbrains.plugins.scala.lang.psi.api.statements

import com.intellij.psi.util.PsiTreeUtil
import expr.ScBlock
import icons.Icons
import javax.swing.Icon
import params.ScParameterClause
import toplevel.templates.ScExtendsBlock
import toplevel.{ScTyped, ScTypeParametersOwner}
import types.ScType
import psi.ScalaPsiElement
import toplevel.typedef._
import com.intellij.psi._
import base.types.ScTypeElement
import expr.ScBlockStatement

/**
 * @author AlexanderPodkhalyuzin
* Date: 08.04.2008
 */

trait ScValue extends ScBlockStatement with ScMember with ScDocCommentOwner with ScDeclaredElementsHolder {
  def declaredElements: Seq[ScTyped]
  def typeElement = findChild(classOf[ScTypeElement])

  def declaredType = typeElement match {
    case Some(te) => Some(te.getType)
    case None => None
  }

  def getType: ScType


  override def getIcon(flags: Int): Icon = {
    import Icons._
    var parent = getParent
    while (parent != null) {
      parent match {
        case _: ScExtendsBlock => return FIELD_VAL
        case _: ScBlock => return VAL
        case _ => parent = parent.getParent
      }
    }
    null
  }

}