package org.jetbrains.plugins.scala.lang.psi.api.base.types

import _root_.scala.collection.immutable.HashSet
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

  def calcType: ScType = getType(HashSet[ScNamedElement]()).resType
}

case class ScTypeInferenceResult(resType: ScType, isCyclic: Boolean, cycleStart: Option[ScNamedElement])