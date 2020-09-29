package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScDocCommentOwner

trait ScExtension extends ScTypeParametersOwner
  with ScParameterOwner
  with ScDocCommentOwner
  with ScCommentOwner
{
  def targetTypeElement: Option[ScTypeElement]
  def extensionMethods: Seq[ScFunctionDefinition]
  def extensionBody: Option[ScTemplateBody]
}
