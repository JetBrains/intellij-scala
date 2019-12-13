package org.jetbrains.plugins.scala
package annotator
package element

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParamClause
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeBoundsOwner

object ScTypeBoundsOwnerAnnotator extends ElementAnnotator[ScTypeBoundsOwner] {

  override def annotate(element: ScTypeBoundsOwner, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    if (!Option(PsiTreeUtil.getParentOfType(element, classOf[ScTypeParamClause])).flatMap(_.parent).exists(_.isInstanceOf[ScFunction])) {
      for {
        lower <- element.lowerBound.toOption
        upper <- element.upperBound.toOption
        if !lower.conforms(upper)
        annotation = holder.createErrorAnnotation(element,
          ScalaBundle.message("lower.bound.conform.to.upper", upper, lower))
      } annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR)
    }
  }
}
