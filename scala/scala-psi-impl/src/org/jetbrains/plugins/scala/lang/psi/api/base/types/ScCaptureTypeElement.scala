package org.jetbrains.plugins.scala.lang.psi.api.base.types

import org.jetbrains.plugins.scala.lang.psi.api.base.types.cc.ScCaptureSet

trait ScCaptureTypeElement extends ScTypeElement {
  override protected val typeName = "CaptureType"

  def innerElement: ScTypeElement = findChild[ScTypeElement].get
  def captureSet: Option[ScCaptureSet] = findLastChild[ScCaptureSet]
}
