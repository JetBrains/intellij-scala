package org.jetbrains.plugins.scala.codeInspection.typeLambdaSimplify

import com.intellij.codeInspection.{LocalQuickFix, ProblemDescriptor, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.typeLambdaSimplify.KindProjectorUseCorrectLambdaKeywordInspection._
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractInspection}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParameterizedTypeElement, ScSimpleTypeElement}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createTypeElementFromText

/**
 * Author: Svyatoslav Ilinskiy
 * Date: 6/25/15
 */
class KindProjectorUseCorrectLambdaKeywordInspection extends AbstractInspection(inspectionId, inspectionName) {

  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case param: ScParameterizedTypeElement if ScalaPsiUtil.kindProjectorPluginEnabled(param) =>
      val useGreekLambda = ScalaCodeStyleSettings.getInstance(param.getProject).REPLACE_LAMBDA_WITH_GREEK_LETTER
      param.children.foreach {
        case simple: ScSimpleTypeElement =>
          simple.getText match {
            case "Lambda" if useGreekLambda =>
              val changeKeywordFix = new KindProjectorUseCorrectLambdaKeywordQuickFix(simple, "λ")
              holder.registerProblem(simple, "Kind Projector: Replace Lambda with λ", changeKeywordFix)
              val changeSettingsFix = new ChangeLambdaCodeStyleSetting(!useGreekLambda)
              holder.registerProblem(simple, codeStyleSettingUseWordLambda, changeSettingsFix)
            case "λ" if !useGreekLambda =>
              val changeKeywordFix = new KindProjectorUseCorrectLambdaKeywordQuickFix(simple, "Lambda")
              holder.registerProblem(simple, "Kind Projector: Replace λ with Lambda", changeKeywordFix)
              val changeSettingsFix = new ChangeLambdaCodeStyleSetting(!useGreekLambda)
              holder.registerProblem(simple, codeStyleSettingUseGreekLambda, changeSettingsFix)
            case _ =>
          }
        case _ =>

      }
  }
}

class KindProjectorUseCorrectLambdaKeywordQuickFix(e: PsiElement, replacement: String) extends AbstractFixOnPsiElement(inspectionName, e) {

  override protected def doApplyFix(elem: PsiElement)
                                   (implicit project: Project): Unit = {
    elem.replace(createTypeElementFromText(replacement))
  }
}

class ChangeLambdaCodeStyleSetting(useGreekLambda: Boolean) extends LocalQuickFix {
  override def getFamilyName: String = getName

  override def getName: String =
    if (useGreekLambda) codeStyleSettingUseGreekLambda
    else codeStyleSettingUseWordLambda

  override def applyFix(project: Project, d: ProblemDescriptor): Unit = {
    ScalaCodeStyleSettings.getInstance(project).REPLACE_LAMBDA_WITH_GREEK_LETTER = useGreekLambda
  }
}

object KindProjectorUseCorrectLambdaKeywordInspection {
  val inspectionName = "Kind Projector: Use correct lambda keyword"
  val inspectionId = "KindProjectorUseCorrectLambdaKeyword"
  val codeStyleSettingUseGreekLambda = "Kind Projector: Change code style setting: use λ instead of Lambda"
  val codeStyleSettingUseWordLambda = "Kind Projector: Change code style setting: use Lambda instead of λ"
}
