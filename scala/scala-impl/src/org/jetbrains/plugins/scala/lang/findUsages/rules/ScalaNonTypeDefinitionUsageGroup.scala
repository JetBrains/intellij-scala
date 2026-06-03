package org.jetbrains.plugins.scala.lang.findUsages.rules

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember

private class ScalaNonTypeDefinitionUsageGroup(member: ScMember, name: String) extends ScalaDeclarationUsageGroupBase(member, name) {
  override def getPresentableGroupText: String = name
}