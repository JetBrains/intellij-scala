package org.jetbrains.plugins.scala.codeInspection.typeLambdaSimplify

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.typeLambdaSimplify.KindProjectorReplaceLambdaWithLetterInspection.{inspectionId, inspectionName}
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractInspection}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScSimpleTypeElement, ScParameterizedTypeElement}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
 * Author: Svyatoslav Ilinskiy
 * Date: 6/25/15
 */
class KindProjectorReplaceLambdaWithLetterInspection extends AbstractInspection(inspectionId, inspectionName) {


  override def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case param: ScParameterizedTypeElement if ScalaPsiUtil.kindProjectorPluginEnabled(param) =>
      param.children.foreach {
        case simple: ScSimpleTypeElement if simple.getText == "Lambda" =>
          val fix = new KindProjectorReplaceLambdaWithLetterQuickFix(simple)
          holder.registerProblem(simple, inspectionName, fix)
        case _ =>

      }
  }
}

class KindProjectorReplaceLambdaWithLetterQuickFix(e: PsiElement) extends AbstractFixOnPsiElement(inspectionName, e) {
  override def doApplyFix(project: Project): Unit = {
    val elem = getElement
    if (!elem.isValid) return

    val repl = ScalaPsiElementFactory.createTypeElementFromText("λ", elem.getManager)
    elem.replace(repl)
  }
}

object KindProjectorReplaceLambdaWithLetterInspection {
  val inspectionName = "Kind Projector: Replace Lambda with λ"
  val inspectionId = "KindProjectorReplaceLambdaWithLetter"
}
