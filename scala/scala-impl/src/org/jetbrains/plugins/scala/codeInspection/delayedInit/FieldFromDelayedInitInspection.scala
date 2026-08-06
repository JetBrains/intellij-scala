package org.jetbrains.plugins.scala.codeInspection.delayedInit

import com.intellij.codeInspection.{LocalInspectionTool, ProblemsHolder}
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.codeInspection.{PsiElementVisitorSimple, ScalaInspectionBundle, conformsToTypeFromClass}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScPatternDefinition, ScValueOrVariable, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.Context
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

final class FieldFromDelayedInitInspection extends LocalInspectionTool {

  import FieldFromDelayedInitInspection._

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = PsiElementVisitorSimple(holder) {
    case ref: ScReferenceExpression =>
      implicit val context: Context = Context(ref)

      for {
        FieldInDelayedInit(delayedInitClass) <- ref.bind()
        parents = parentDefinitions(ref)
        if !parents.exists(_.sameOrInheritor(delayedInitClass))
      } holder.registerProblem(ref.nameId, ScalaInspectionBundle.message("field.defined.in.delayedinit.is.likely.to.be.null"))
    case _ =>
  }
}

object FieldFromDelayedInitInspection {

  private object FieldInDelayedInit {

    def unapply(result: ScalaResolveResult)(implicit context: Context): Option[ScTemplateDefinition] =
      result.fromType.flatMap { scType =>
        result.getElement.nameContext match {
          case LazyVal(_) => None
          case definition@(_: ScPatternDefinition | _: ScVariableDefinition) =>
            Option(definition.asInstanceOf[ScValueOrVariable].containingClass).collect {
              case scalaClass: ScClass => scalaClass
              case scalaObject: ScObject => scalaObject
            }.filter(conformsToTypeFromClass(scType, "scala.DelayedInit")(_, context))
          case _ => None
        }
      }
  }

  private def parentDefinitions(reference: ScReferenceExpression) =
    reference.parentsInFile.collect {
      case definition: ScTemplateDefinition => definition
    }
}
