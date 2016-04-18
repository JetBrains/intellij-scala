package org.jetbrains.plugins.dotty.lang.psi.types

import org.jetbrains.plugins.scala.lang.psi.types.api.TypeVisitor

/**
  * @author adkozlov
  */
trait DottyTypeVisitor extends TypeVisitor {
  def visitNoType(noType: DottyNoType.type) {}

  def visitAndType(andType: DottyAndType) {}

  def visitOrType(orType: DottyOrType) {}

  def visitRefinedType(refinedType: DottyRefinedType) {}
}
