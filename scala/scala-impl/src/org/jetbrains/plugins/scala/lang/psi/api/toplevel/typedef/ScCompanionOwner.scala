package org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

trait ScCompanionOwner extends ScNamedElement with ScMember with ScMember.WithBaseIconProvider {
  def isObject: Boolean = false

  def baseCompanion: Option[ScCompanionOwner]

  def baseCompanionTypeDefinition: Option[ScTypeDefinition]
}
