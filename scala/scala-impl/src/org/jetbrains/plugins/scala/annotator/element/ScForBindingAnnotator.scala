package org.jetbrains.plugins.scala
package annotator
package element

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.codeInspection.caseClassParamInspection.{RemoveCaseFromForBindingIntentionAction, RemoveValFromForBindingIntentionAction}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScForBinding

object ScForBindingAnnotator extends ElementAnnotator[ScForBinding] {

  override def annotate(element: ScForBinding, typeAware: Boolean)
                       (implicit holder: AnnotationHolder): Unit = {

    element.valKeyword.foreach { valKeyword =>
      val annotation = holder.createWarningAnnotation(valKeyword, ScalaBundle.message("enumerators.binding.val.keyword.deprecated"))
      annotation.setHighlightType(ProblemHighlightType.LIKE_DEPRECATED)
      annotation.registerFix(new RemoveValFromForBindingIntentionAction(element))
    }

    // TODO: this is quite the same as ScGeneratorAnnotator.annotate has
    // looks like the presentation of these two errors is not the best, maybe rethink?
    element.caseKeyword.foreach { caseKeyword =>
      val annotation = holder.createWarningAnnotation(caseKeyword, ScalaBundle.message("enumerators.binding.case.keyword.found"))
      annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
      annotation.registerFix(new RemoveCaseFromForBindingIntentionAction(element))
    }
  }
}
