package org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

trait ScTypeDefinitionLike extends ScNamedElement with ScMember with ScMember.WithBaseIconProvider {
  def isObject: Boolean = false

  def canHaveCompanion: Boolean = true

  def baseCompanion: Option[ScTypeDefinitionLike]

  def baseCompanionTypeDefinition: Option[ScTypeDefinition]
}
