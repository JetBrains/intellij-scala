package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import org.jetbrains.plugins.scala.lang.psi.api._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScTypeParametersOwner, ScTypeParametersOwnerBase}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScDocCommentOwner, ScDocCommentOwnerBase}

trait ScExtensionBase extends ScTypeParametersOwnerBase with ScParameterOwnerBase with ScDocCommentOwnerBase with ScCommentOwnerBase { this: ScExtension =>
  def targetTypeElement: Option[ScTypeElement]
  def extensionMethods: Seq[ScFunctionDefinition]
  def extensionBody: Option[ScTemplateBody]
}