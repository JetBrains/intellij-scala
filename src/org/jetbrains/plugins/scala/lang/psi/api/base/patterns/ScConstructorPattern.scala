package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

/**
* @author Alexander Podkhalyuzin
* Patterns, introduced by case classes or extractors
*/
trait ScConstructorPattern extends ScPattern {
  def args: ScPatternArgumentList = findChildByClassScala(classOf[ScPatternArgumentList])
  def ref: ScStableCodeReferenceElement = findChildByClassScala(classOf[ScStableCodeReferenceElement])
}