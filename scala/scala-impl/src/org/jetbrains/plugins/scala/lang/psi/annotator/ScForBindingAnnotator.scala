package org.jetbrains.plugins.scala.lang.psi.annotator

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInspection.caseClassParamInspection.RemoveValFromForBindingIntentionAction
import org.jetbrains.plugins.scala.lang.psi.api.Annotatable
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScForBinding


trait ScForBindingAnnotator extends Annotatable { self: ScForBinding =>

  override def annotate(holder: AnnotationHolder, typeAware: Boolean): Unit = {
    super.annotate(holder, typeAware)

    valKeyword match {
      case Some(valKeyword) =>
        val annotation = holder.createWarningAnnotation(valKeyword, ScalaBundle.message("enumerator.val.keyword.deprecated"))
        annotation.setHighlightType(ProblemHighlightType.LIKE_DEPRECATED)
        annotation.registerFix(new RemoveValFromForBindingIntentionAction(this))
      case _ =>
    }
  }
}
