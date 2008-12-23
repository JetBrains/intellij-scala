package org.jetbrains.plugins.scala.lang.psi.api.base.types

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.types._
import com.intellij.psi._
import toplevel.ScNamedElement

/**
* @author Alexander Podkhalyuzin
* Date: 14.04.2008
*/

trait ScTypeElement extends ScalaPsiElement {
  def getType(implicit visited: Set[ScNamedElement]) : ScTypeInferenceResult = ScTypeInferenceResult(Nothing, false, None)
}

case class ScTypeInferenceResult(resType: ScType, isCyclic: Boolean, cycleStart: Option[ScNamedElement])