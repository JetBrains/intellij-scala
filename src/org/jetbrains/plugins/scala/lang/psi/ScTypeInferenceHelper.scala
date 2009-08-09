package org.jetbrains.plugins.scala
package lang
package psi

import _root_.org.jetbrains.plugins.scala.lang.psi.types.ScType
import api.base.types.ScTypeInferenceResult
import api.toplevel.ScNamedElement

/**
 * @author ilyas
 */

trait ScTypeInferenceHelper {
  protected implicit def _res2type(res: ScTypeInferenceResult): ScType = res match {
    case ScTypeInferenceResult(_, true, _) => types.Nothing //todo ?
    case ScTypeInferenceResult(res, _, _) => res
  }

  protected implicit def _type2res(t: ScType) = ScTypeInferenceResult(t, false, None) 

  implicit val _emptyVisitedSet: Set[ScNamedElement] = Set[ScNamedElement]()
}