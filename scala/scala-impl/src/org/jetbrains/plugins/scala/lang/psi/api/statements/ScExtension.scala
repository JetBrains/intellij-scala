package org.jetbrains.plugins.scala.lang.psi.api.statements

import org.jetbrains.plugins.scala.lang.psi.ScExportsHolder
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScDocCommentOwner, ScMember}

trait ScExtension extends ScParameterOwner.WithContextBounds
  with ScDocCommentOwner
  with ScCommentOwner
  //TODO: extension is technically not a member.
  // It's "BlockStat", "TopStat" and "TemplateStat" (see https://docs.scala-lang.org/scala3/reference/syntax.html)
  // For example it can't have modifiers (but methods in the extension can have them)
  // Extending ScMember can lead to unexpected issues
  // We need to rethink the hierarchy for extensions.
  //NOTE: it's also extended in ScExtensionImpl
  with ScMember
  with ScDeclaredElementsHolder {

  def extensionBody: Option[ScExtensionBody]
  def targetParameter: Option[ScParameter]
  def targetTypeElement: Option[ScTypeElement]
  def extensionMethods: Seq[ScFunction]
}
