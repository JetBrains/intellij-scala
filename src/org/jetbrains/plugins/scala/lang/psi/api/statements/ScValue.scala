package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import com.intellij.psi.util.PsiTreeUtil
import expr.ScBlock
import icons.Icons
import javax.swing.Icon
import params.ScParameterClause
import toplevel.templates.ScExtendsBlock
import toplevel.{ScTypedDefinition, ScTypeParametersOwner}
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

trait ScValue extends ScBlockStatement with ScMember with ScDocCommentOwner with ScDeclaredElementsHolder with ScAnnotationsHolder {
  def declaredElements: Seq[ScTypedDefinition]
  def typeElement: Option[ScTypeElement]

  def declaredType : Option[ScType] = typeElement match {
    case Some(te) => Some(te.cachedType)
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