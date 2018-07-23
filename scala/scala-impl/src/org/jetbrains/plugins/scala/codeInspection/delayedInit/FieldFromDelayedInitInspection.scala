package org.jetbrains.plugins.scala
package codeInspection
package delayedInit

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScPatternDefinition, ScValueOrVariable, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

/**
  * @author Nikolay.Tropin
  */
final class FieldFromDelayedInitInspection extends AbstractInspection("Field from DelayedInit") {

  import FieldFromDelayedInitInspection._

  override protected def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case ref: ScReferenceExpression =>
      for {
        FieldInDelayedInit(delayedInitClass) <- ref.bind()
        parents = parentDefinitions(ref)
        if !parents.exists(_.sameOrInheritor(delayedInitClass))
      } holder.registerProblem(ref.nameId, "Field defined in DelayedInit is likely to be null")
  }
}

object FieldFromDelayedInitInspection {

  private object FieldInDelayedInit {

    def unapply(result: ScalaResolveResult): Option[ScTemplateDefinition] =
      result.fromType.flatMap { scType =>
        ScalaPsiUtil.nameContext(result.getElement) match {
          case LazyVal(_) => None
          case definition@(_: ScPatternDefinition | _: ScVariableDefinition) =>
            Option(definition.asInstanceOf[ScValueOrVariable].containingClass).collect {
              case scalaClass: ScClass => scalaClass
              case scalaObject: ScObject => scalaObject
            }.filter(conformsToTypeFromClass(scType, "scala.DelayedInit")(_))
          case _ => None
        }
      }
  }

  private def parentDefinitions(reference: ScReferenceExpression) =
    reference.parentsInFile.collect {
      case definition: ScTemplateDefinition => definition
    }
}
