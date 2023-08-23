package org.jetbrains.plugins.scala.lang.psi.api.base
package types

trait ScTypeProjection extends ScTypeElement with ScReference {
  override protected val typeName = "TypeProjection"

  def typeElement: ScTypeElement = findChild[ScTypeElement].get
}