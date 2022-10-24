package org.jetbrains.plugins.scala.lang.psi.api.statements

trait ScTypeAliasDeclaration extends ScTypeAlias with ScDeclaration {
  override def declaredElements = Seq(this)

  override def isDefinition: Boolean = false
}