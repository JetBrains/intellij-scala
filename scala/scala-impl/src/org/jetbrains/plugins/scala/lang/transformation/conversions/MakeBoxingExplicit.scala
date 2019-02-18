package org.jetbrains.plugins.scala.lang.transformation
package conversions

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotationsHolder
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ExpectedType, ScExpression, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.project.ProjectContext


/**
  * @author Pavel Fatin
  */
// Boxing/unboxing is not a syntactic sugar but rather a JVM-specific implementation detail.
// We should not show this conversion in the Desugar Scala code dialog,
// but we may reuse this code for an inspection or an editor mode that reveals boxing/unboxing.
class MakeBoxingExplicit extends AbstractTransformer {
  private val Class = "scala.runtime.BoxesRunTime"

  def boxMethodName(t: ScType): Option[String] = {
    t match {
      case _ if t.isUnit => None
      case v: ValType =>
        val postfix = v.name match {
          case "Char" => "Character"
          case "Int" => "Integer"
          case name => name
        }
        Some(s"boxTo$postfix")
      case _ => None
    }
  }

  protected def transformation(implicit project: ProjectContext): PartialFunction[PsiElement, Unit] = {
    case (e: ScExpression) && Typeable(t) && ExpectedType(et)
      if boxMethodName(t).nonEmpty && et != AnyRef && et != t && !isSpecializedFor(et, t) =>

      val target = s"$Class.${boxMethodName(t).get}"

      val FirstChild(r: ScReferenceExpression) = e.replace(code"${simpleNameOf(target)}($e)")
      bindTo(r, target)
  }

  private def isSpecializedFor(target: ScType, source: ScType): Boolean = target match {
    case it: TypeParameterType =>
      isSpecializedFor(it.psiTypeParameter.asInstanceOf[ScAnnotationsHolder], source)
    case _ =>
      false
  }

  private def isSpecializedFor(holder: ScAnnotationsHolder, t: ScType): Boolean = {
    holder.annotations.exists { it =>
      val name = it.annotationExpr.constr.typeElement.getText
      val arguments = it.annotationExpr.getAnnotationParameters
      name == "specialized" && (arguments.isEmpty || arguments.exists(_.getText == t.presentableText))
    }
  }
}
