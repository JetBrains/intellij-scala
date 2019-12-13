package org.jetbrains.plugins.scala
package annotator
package element

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.caseClassParamInspection.RemoveValFromForBindingIntentionAction
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScForBinding

object ScForBindingAnnotator extends ElementAnnotator[ScForBinding] {

  override def annotate(element: ScForBinding, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    findValKeyword(element).foreach {
      valKeyword =>
        val annotation = holder.createWarningAnnotation(valKeyword, ScalaBundle.message("enumerator.val.keyword.deprecated"))
        annotation.setHighlightType(ProblemHighlightType.LIKE_DEPRECATED)
        annotation.registerFix(new RemoveValFromForBindingIntentionAction(element))
    }
  }

  private def findValKeyword(binding: ScForBinding): Option[PsiElement] =
    Option(binding.getNode.findChildByType(ScalaTokenTypes.kVAL)).map(_.getPsi)
}
