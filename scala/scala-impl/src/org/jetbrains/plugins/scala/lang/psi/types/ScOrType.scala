package org.jetbrains.plugins.scala.lang.psi.types

import org.jetbrains.plugins.scala.lang.psi.types.api.ValueType
import org.jetbrains.plugins.scala.project.ProjectContext

/**
 * Scala 3 union type, e.g. `Foo | Bar`
 */
final case class ScOrType private(lhs: ScType, rhs: ScType) extends ScalaType with ValueType {
  override def visitType(visitor: ScalaTypeVisitor): Unit = visitor.visitOrType(this)

  override implicit def projectContext: ProjectContext = lhs.projectContext

  //@TODO
  def join: ScType = lhs.lub(rhs)
}

object ScOrType {
  def apply(lhs: ScType, rhs: ScType): ScType =
    if (lhs == rhs) lhs
    else            new ScOrType(lhs, rhs)
}
