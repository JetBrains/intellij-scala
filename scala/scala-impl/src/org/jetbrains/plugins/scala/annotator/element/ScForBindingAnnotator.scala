package org.jetbrains.plugins.scala.annotator.element

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInspection.caseClassParamInspection.RemoveValFromForBindingIntentionAction
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScForBinding

object ScForBindingAnnotator extends ElementAnnotator[ScForBinding] {
  override def annotate(element: ScForBinding, holder: AnnotationHolder, typeAware: Boolean): Unit = {
    element.valKeyword match {
      case Some(valKeyword) =>
        val annotation = holder.createWarningAnnotation(valKeyword, ScalaBundle.message("enumerator.val.keyword.deprecated"))
        annotation.setHighlightType(ProblemHighlightType.LIKE_DEPRECATED)
        annotation.registerFix(new RemoveValFromForBindingIntentionAction(element))
      case _ =>
    }
  }
}
