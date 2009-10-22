package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import expr.ScBlock
import javax.swing.Icon
import toplevel.templates.ScExtendsBlock
import toplevel.{ScTypedDefinition}
import types.ScType
import toplevel.typedef._
import base.types.ScTypeElement
import expr.ScBlockStatement
import org.jetbrains.plugins.scala.lang.psi.types.Any
import icons.Icons
import types.result.{TypeResult, TypingContext, Success}

/**
 * @author Alexander Podkhalyuzin
 */

trait ScValue extends ScBlockStatement with ScMember with ScDocCommentOwner with ScDeclaredElementsHolder with ScAnnotationsHolder {
  def declaredElements: Seq[ScTypedDefinition]
  def typeElement: Option[ScTypeElement]

  def declaredType: Option[ScType] = typeElement flatMap (_.cachedType match {
    case Success(t, _) => Some(t)
    case _ => None
  })

  def getType(ctx: TypingContext): TypeResult[ScType]

  override def getIcon(flags: Int): Icon = {
    var parent = getParent
    while (parent != null) {
      parent match {
        case _: ScExtendsBlock => return Icons.FIELD_VAL
        case _: ScBlock => return Icons.VAL
        case _ => parent = parent.getParent
      }
    }
    null
  }

}