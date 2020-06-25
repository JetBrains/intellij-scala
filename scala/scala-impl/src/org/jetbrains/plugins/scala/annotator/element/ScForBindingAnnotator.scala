package org.jetbrains.plugins.scala
package annotator
package element

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInspection.caseClassParamInspection.RemoveValFromForBindingIntentionAction
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScForBinding, ScPatternedEnumerator}

object ScForBindingAnnotator extends ElementAnnotator[ScForBinding] {

  override def annotate(element: ScForBinding, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    element.valKeyword.foreach { valKeyword =>
      val annotation = holder.createWarningAnnotation(valKeyword, ScalaBundle.message("enumerators.binding.val.keyword.deprecated"))
      annotation.setHighlightType(ProblemHighlightType.LIKE_DEPRECATED)
      annotation.registerFix(new RemoveValFromForBindingIntentionAction(element))
    }

    // TODO: this is quite the same as ScGeneratorAnnotator.annotate has
    //  looks like the presentation (message and style) of these two errors is not the best, maybe rethink?
    element.caseKeyword.foreach { caseKeyword =>
      val annotation = holder.createWarningAnnotation(caseKeyword, ScalaBundle.message("enumerators.binding.case.keyword.found"))
      annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR)
      annotation.registerFix(new RemoveCaseFromPatternedEnumeratorFix(element))
    }
  }

  class RemoveCaseFromPatternedEnumeratorFix(enumerator: ScPatternedEnumerator) extends IntentionAction {

    override def getFamilyName: String = ScalaBundle.message("family.name.remove.case.from.enumerator")

    override def getText: String = ScalaBundle.message("remove.case")

    override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = true

    override def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
      if (!enumerator.isValid) return
      enumerator.findChildrenByType(ScalaTokenTypes.kCASE).foreach(_.delete())
    }

    override def startInWriteAction(): Boolean = true
  }
}
