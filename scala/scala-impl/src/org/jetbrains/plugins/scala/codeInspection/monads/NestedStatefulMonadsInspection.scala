package org.jetbrains.plugins.scala.codeInspection.monads

import com.intellij.codeInspection.{LocalInspectionTool, ProblemsHolder}
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.codeInspection.{PsiElementVisitorSimple, ScalaInspectionBundle, conformsToTypeFromClass}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.ParameterizedType
import org.jetbrains.plugins.scala.lang.psi.types.result._

final class NestedStatefulMonadsInspection extends LocalInspectionTool {

  import NestedStatefulMonadsInspection._

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case call: ScMethodCall =>
      implicit val project: Project = holder.getProject
      for {
        Typeable(genericType@ParameterizedType(_, arguments)) <- Some(call)
        if isStatefulMonadType(genericType) && arguments.exists(isStatefulMonadType)
      } holder.registerProblem(call, Description)
    case _ =>
  }
}

object NestedStatefulMonadsInspection {
  @Nls
  private[monads] final val Description = ScalaInspectionBundle.message("displayname.nested.stateful.monads")

  private final val StatefulMonadsTypesNames = Set("scala.concurrent.Future", "scala.util.Try")

  private def isStatefulMonadType(scType: ScType)
                                 (implicit project: Project): Boolean =
    StatefulMonadsTypesNames.exists(conformsToTypeFromClass(scType, _))
}