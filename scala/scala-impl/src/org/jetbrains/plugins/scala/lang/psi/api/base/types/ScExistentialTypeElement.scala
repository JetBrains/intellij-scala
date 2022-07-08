package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

trait ScExistentialTypeElement extends ScTypeElement {
  override protected val typeName = "ExistentialType"

  def quantified: ScTypeElement = findChild[ScTypeElement].get
  def clause: ScExistentialClause = findChild[ScExistentialClause].get
}