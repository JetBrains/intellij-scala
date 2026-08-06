package org.jetbrains.plugins.scala.codeInspection.monads

import com.intellij.codeInspection.{LocalInspectionTool, ProblemsHolder}
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.codeInspection.{PsiElementVisitorSimple, ScalaInspectionBundle, conformsToTypeFromClass}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall
import org.jetbrains.plugins.scala.lang.psi.types.{Context, ScType}
import org.jetbrains.plugins.scala.lang.psi.types.api.ParameterizedType
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.project.ProjectContext

final class NestedStatefulMonadsInspection extends LocalInspectionTool {

  import NestedStatefulMonadsInspection._

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = PsiElementVisitorSimple(holder) {
    case call: ScMethodCall =>
      import call.projectContext
      implicit val context: Context = Context(call)

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
                                 (implicit projectContext: ProjectContext, context: Context): Boolean =
    StatefulMonadsTypesNames.exists(conformsToTypeFromClass(scType, _))
}