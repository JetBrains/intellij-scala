package org.jetbrains.plugins.scala.lang.psi.types

import org.jetbrains.plugins.scala.lang.psi.types.api.ValueType
import org.jetbrains.plugins.scala.project.ProjectContext

/**
 * Scala 3 intersection type, e.g. `Foo & Bar`
 */
final case class ScAndType private(lhs: ScType, rhs: ScType) extends ScalaType with ValueType {
  override def visitType(visitor: ScalaTypeVisitor): Unit = visitor.visitAndType(this)

  override implicit def projectContext: ProjectContext = lhs.projectContext
}

object ScAndType {
  def apply(lhs: ScType, rhs: ScType): ScType = {
    if (lhs == rhs || rhs.isAny) lhs
    else if (lhs.isAny)          rhs
    else                         new ScAndType(lhs, rhs)
  }
}
