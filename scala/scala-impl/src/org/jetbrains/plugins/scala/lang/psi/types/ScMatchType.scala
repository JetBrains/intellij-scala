package org.jetbrains.plugins.scala.lang.psi.types

import org.jetbrains.plugins.scala.lang.psi.types.api.ValueType
import org.jetbrains.plugins.scala.project.ProjectContext

final class ScMatchType private (val scrutinee: ScType, val cases: Seq[(ScType, ScType)]) extends ScalaType with ValueType {
  override def visitType(visitor: ScalaTypeVisitor): Unit = visitor.visitMatchType(this)

  override implicit def projectContext: ProjectContext = scrutinee.projectContext
}

object ScMatchType {
  def apply(scrutinee: ScType, cases: Seq[(ScType, ScType)]) = new ScMatchType(scrutinee, cases)

  def unapply(mt: ScMatchType): Some[(ScType, Seq[(ScType, ScType)])] = Some((mt.scrutinee, mt.cases))
}
