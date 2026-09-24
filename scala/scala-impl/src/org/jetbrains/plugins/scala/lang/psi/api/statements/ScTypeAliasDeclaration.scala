package org.jetbrains.plugins.scala.lang.psi.api.statements

import org.jetbrains.plugins.scala.project.ProjectPsiElementExt

trait ScTypeAliasDeclaration extends ScTypeAlias with ScDeclaration {
  override def declaredElements: Seq[ScTypeAliasDeclaration] = Seq(this)

  override def isDefinition: Boolean = false

  override def canHaveCompanion: Boolean = this.isInScala3Module
}