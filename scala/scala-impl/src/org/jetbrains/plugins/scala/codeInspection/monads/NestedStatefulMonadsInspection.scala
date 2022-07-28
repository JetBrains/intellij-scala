package org.jetbrains.plugins.scala
package codeInspection
package monads

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.ParameterizedType
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.annotation.nowarn

@nowarn("msg=" + AbstractInspection.DeprecationText)
final class NestedStatefulMonadsInspection extends AbstractInspection(NestedStatefulMonadsInspection.Description) {

  import NestedStatefulMonadsInspection._

  override def actionFor(implicit holder: ProblemsHolder, isOnTheFly: Boolean): PartialFunction[PsiElement, Unit] = {
    case call: ScMethodCall =>
      import call.projectContext
      for {
        Typeable(genericType@ParameterizedType(_, arguments)) <- Some(call)
        if isStatefulMonadType(genericType) && arguments.exists(isStatefulMonadType)
      } holder.registerProblem(call, Description)
  }
}

object NestedStatefulMonadsInspection {
  @Nls
  private[monads] final val Description = ScalaInspectionBundle.message("nested.stateful.monads")

  private final val StatefulMonadsTypesNames = Set("scala.concurrent.Future", "scala.util.Try")

  private def isStatefulMonadType(scType: ScType)
                                 (implicit context: ProjectContext): Boolean =
    StatefulMonadsTypesNames.exists(conformsToTypeFromClass(scType, _))
}