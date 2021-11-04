package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScDocCommentOwner, ScMember}

trait ScExtension extends ScParameterOwner.WithContextBounds with ScMarkerOwner
  with ScDocCommentOwner
  with ScCommentOwner
  with ScMember
  with ScDeclaredElementsHolder {

  def extensionBody: Option[ScExtensionBody]
  def targetParameter: Option[ScParameter]
  def targetTypeElement: Option[ScTypeElement]
  def extensionMethods: Seq[ScFunction]
}
