package org.jetbrains.plugins.scala.lang.psi.annotator

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.api.Annotatable
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParamClause
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeBoundsOwner
import org.jetbrains.plugins.scala.extensions._

trait ScTypeBoundsOwnerAnnotator extends Annotatable { self: ScTypeBoundsOwner =>

  override def annotate(holder: AnnotationHolder, typeAware: Boolean): Unit = {
    super.annotate(holder, typeAware)

    if (!Option(PsiTreeUtil.getParentOfType(this, classOf[ScTypeParamClause])).flatMap(_.parent).exists(_.isInstanceOf[ScFunction])) {
      for {
        lower <- lowerBound.toOption
        upper <- upperBound.toOption
        if !lower.conforms(upper)
        annotation = holder.createErrorAnnotation(this,
          ScalaBundle.message("lower.bound.conform.to.upper", upper, lower))
      } annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR)
    }
  }
}
