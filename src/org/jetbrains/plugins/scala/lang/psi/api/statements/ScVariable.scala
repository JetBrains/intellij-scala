package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import expr.{ScBlock, ScBlockStatement}
import javax.swing.Icon
import toplevel.templates.ScExtendsBlock
import toplevel.{ScTypedDefinition}
import types.ScType
import toplevel.typedef._
import base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.types.Any
import icons.Icons
import types.result.{TypingContext, TypeResult}

/**
 * @author Alexander Podkhalyuzin
 */

trait ScVariable extends ScBlockStatement with ScMember with ScDocCommentOwner with ScDeclaredElementsHolder with ScAnnotationsHolder {
  def declaredElements: Seq[ScTypedDefinition]

  def typeElement: Option[ScTypeElement]

  def declaredType: Option[ScType] = typeElement map (_.cachedType.getOrElse(Any))

  def getType(ctx: TypingContext): TypeResult[ScType]

  override def getIcon(flags: Int): Icon = {
    var parent = getParent
    while (parent != null) {
      parent match {
        case _: ScExtendsBlock => return Icons.FIELD_VAR
        case _: ScBlock => return Icons.VAR
        case _ => parent = parent.getParent
      }
    }
    null
  }


}