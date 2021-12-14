package org.jetbrains.plugins.scala.lang.psi.types

import org.jetbrains.plugins.scala.lang.psi.types.api.{ParameterizedType, ValueType}
import org.jetbrains.plugins.scala.project.ProjectContext

/**
 * Scala 3 union type, e.g. `Foo | Bar`
 */
final case class ScOrType private(lhs: ScType, rhs: ScType) extends ScalaType with ValueType {
  private val bounds = Scala3Bounds()

  override def visitType(visitor: ScalaTypeVisitor): Unit = visitor.visitOrType(this)

  override implicit def projectContext: ProjectContext = lhs.projectContext

  def join: ScType = (lhs, rhs) match {
    //@TODO:
//    case (ParameterizedType(lTycon, lArgs), ParameterizedType(rTycon, rArgs)) =>
    case (_, _) => bounds.orTypeJoin(this)
  }
}

object ScOrType {
  def apply(lhs: ScType, rhs: ScType): ScType =
    if (lhs == rhs) lhs
    else            new ScOrType(lhs, rhs)
}
