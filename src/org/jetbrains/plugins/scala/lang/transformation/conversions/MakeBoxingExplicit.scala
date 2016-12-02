package org.jetbrains.plugins.scala.lang.transformation
package conversions

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ExpectedType, ScExpression, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScAnnotationsHolder
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.{StdType, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable


/**
  * @author Pavel Fatin
  */
class MakeBoxingExplicit extends AbstractTransformer {
  private val Class = "scala.runtime.BoxesRunTime"

  private val Methods = Map[ScType, String](
    (StdType.Boolean, "boxToBoolean"),
    (StdType.Char, "boxToCharacter"),
    (StdType.Byte, "boxToByte"),
    (StdType.Short, "boxToShort"),
    (StdType.Int, "boxToInteger"),
    (StdType.Long, "boxToLong"),
    (StdType.Float, "boxToFloat"),
    (StdType.Double, "boxToDouble"))

  def transformation(implicit project: Project): PartialFunction[PsiElement, Unit] = {
    case (e: ScExpression) && Typeable(t) && ExpectedType(et)
      if Methods.contains(t) && et != StdType.AnyRef && et != t && !isSpecializedFor(et, t) =>

      val target = s"$Class.${Methods(t)}"

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
