package org.jetbrains.plugins.scala.codeInspection.targetNameAnnotation

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScArgumentExprList

class EmptyTargetNameInspection extends TargetNameInspectionBase {

  import EmptyTargetNameInspection._

  override protected val findProblemElement: PartialFunction[PsiElement, ProblemElement] = {
    case TargetNameArgument(extName) if extName.getValue.isEmpty =>
      ProblemElement(extName, description = message)
  }
}

object EmptyTargetNameInspection {
  private[targetNameAnnotation] val message = ScalaInspectionBundle.message("targetname.cannot.be.empty")

  object TargetNameArgument {
    def unapply(extName: ScStringLiteral): Option[ScStringLiteral] =
      if (extName.getParent.is[ScArgumentExprList]) {
        extName.parentOfType[ScAnnotation].collect {
          case annotation if annotation.hasQualifiedName(TargetNameAnnotationFQN) => extName
        }
      } else None
  }
}
