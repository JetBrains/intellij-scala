package org.jetbrains.plugins.scala.lang.psi.api.base.types.cc

import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference

trait ScCaptureFilter extends ScalaPsiElement {
  def filterId: ScReference = findChild[ScReference].get
}
