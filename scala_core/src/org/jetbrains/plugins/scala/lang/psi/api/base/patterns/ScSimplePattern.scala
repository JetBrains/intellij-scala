package org.jetbrains.plugins.scala.lang.psi.api.base.patterns

import _root_.org.jetbrains.plugins.scala.lang.psi.types.ScType
import statements.params.ScParameter
import resolve.ScalaResolveResult
import psi.ScalaPsiElement

/** 
* @author Alexander Podkhalyuzin
* Patterns, introduced by case classes or extractors
*/
trait ScConstructorPattern extends ScPattern {
  def args: ScPatternArgumentList
  def ref = findChildByClass(classOf[ScStableCodeReferenceElement])
  def bindParamTypes() : Option[Seq[ScType]]
}