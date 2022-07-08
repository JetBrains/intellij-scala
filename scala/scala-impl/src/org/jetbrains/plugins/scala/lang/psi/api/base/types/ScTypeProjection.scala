package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

trait ScTypeProjection extends ScTypeElement with ScReference {
  override protected val typeName = "TypeProjection"

  def typeElement: ScTypeElement = findChild[ScTypeElement].get
}