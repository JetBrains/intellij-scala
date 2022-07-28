package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

trait ScTypeAliasDeclaration extends ScTypeAlias with ScDeclaration {
  override def declaredElements = Seq(this)

  override def isDefinition: Boolean = false
}