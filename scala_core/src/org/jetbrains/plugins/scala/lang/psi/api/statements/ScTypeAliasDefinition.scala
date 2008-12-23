package org.jetbrains.plugins.scala.lang.psi.api.statements

import _root_.org.jetbrains.plugins.scala.lang.psi.types.ScType
import base.types.{ScTypeInferenceResult, ScTypeElement}
import toplevel.ScNamedElement
/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScTypeAliasDefinition extends ScTypeAlias {
  def aliasedTypeElement = findChildByClass(classOf[ScTypeElement])

  //todo make cached!
  def aliasedType(visited: Set[ScNamedElement]): ScTypeInferenceResult = {
    if (visited.contains(this)) {
      ScTypeInferenceResult(types.Nothing, true, Some(this))
    } else aliasedTypeElement.getType(visited + this)
  }

  def lowerBound = aliasedType(Set[ScNamedElement]())
  def upperBound = aliasedType(Set[ScNamedElement]())
}