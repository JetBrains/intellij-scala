package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package typedef

import org.jetbrains.plugins.scala.lang.psi.api.base.ScGivenSignature
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScCommentOwner, ScDeclaredElementsHolder}

trait ScGiven extends ScalaPsiElement
  with ScNamedElement
  with ScTypedDefinition
  with ScMember.WithBaseIconProvider
  with ScCommentOwner
  with ScDocCommentOwner
  with ScDeclaredElementsHolder
{
  def signature: Option[ScGivenSignature]
}
