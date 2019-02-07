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
  def ref: ScStableCodeReference = findChildByClassScala(classOf[ScStableCodeReference])
}

object ScConstructorPattern {
  def unapply(pattern: ScConstructorPattern): Option[(ScStableCodeReference, ScPatternArgumentList)] =
    Some((pattern.ref, pattern.args))
}