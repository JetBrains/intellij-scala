package org.jetbrains.plugins.scala
package lang
package structureView

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiPresentationUtils
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor

private object StructureViewUtil {

  def getParametersAsString(x: ScParameters, short: Boolean = true, subst: ScSubstitutor = ScSubstitutor.empty): String =
    ScalaPsiPresentationUtils.renderParametersAsString(x, short, subst)
}