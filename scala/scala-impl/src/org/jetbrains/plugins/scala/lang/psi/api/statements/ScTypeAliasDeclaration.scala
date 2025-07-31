package org.jetbrains.plugins.scala.lang.psi.api.statements

trait ScTypeAliasDeclaration extends ScTypeAlias with ScDeclaration {
  override def declaredElements: Seq[ScTypeAliasDeclaration] = Seq(this)

  override def isDefinition: Boolean = false

  override def canHaveCompanion: Boolean = this.isInScala3Module
}