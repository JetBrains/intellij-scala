package org.jetbrains.plugins.scala.externalLibraries.kindProjector.inspections

import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.{LocalInspectionTool, LocalQuickFix, ProblemDescriptor, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, PsiElementVisitorSimple, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.externalLibraries.kindProjector.inspections.KindProjectorUseCorrectLambdaKeywordInspection._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParameterizedTypeElement, ScSimpleTypeElement}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createTypeElementFromText

class KindProjectorUseCorrectLambdaKeywordInspection extends LocalInspectionTool {

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case param: ScParameterizedTypeElement if param.kindProjectorPluginEnabled =>
      val useGreekLambda = ScalaCodeStyleSettings.getInstance(param.getProject).REPLACE_LAMBDA_WITH_GREEK_LETTER
      param.children.foreach {
        case simple: ScSimpleTypeElement =>
          simple.getText match {
            case "Lambda" if useGreekLambda =>
              val changeKeywordFix = new KindProjectorUseCorrectLambdaKeywordQuickFix(simple, "λ")
              holder.registerProblem(simple, ScalaInspectionBundle.message("kind.projector.replace.lambda.with.lambda.char"), changeKeywordFix)
              val changeSettingsFix = new ChangeLambdaCodeStyleSetting(!useGreekLambda)
              holder.registerProblem(simple, codeStyleSettingUseWordLambda, changeSettingsFix)
            case "λ" if !useGreekLambda =>
              val changeKeywordFix = new KindProjectorUseCorrectLambdaKeywordQuickFix(simple, "Lambda")
              holder.registerProblem(simple, ScalaInspectionBundle.message("kind.projector.replace.lambda.char.with.lambda"), changeKeywordFix)
              val changeSettingsFix = new ChangeLambdaCodeStyleSetting(!useGreekLambda)
              holder.registerProblem(simple, codeStyleSettingUseGreekLambda, changeSettingsFix)
            case _ =>
          }
        case _ =>

      }
    case _ =>
  }
}

final class KindProjectorUseCorrectLambdaKeywordQuickFix(e: PsiElement, @SafeFieldForPreview replacement: String) extends AbstractFixOnPsiElement(inspectionName, e) {

  override protected def doApplyFix(elem: PsiElement)
                                   (implicit project: Project): Unit = {
    elem.replace(createTypeElementFromText(replacement, elem))
  }
}

final class ChangeLambdaCodeStyleSetting(useGreekLambda: Boolean) extends LocalQuickFix {
  override def getFamilyName: String = getName

  override def getName: String =
    if (useGreekLambda) codeStyleSettingUseGreekLambda
    else codeStyleSettingUseWordLambda

  override def applyFix(project: Project, d: ProblemDescriptor): Unit = {
    ScalaCodeStyleSettings.getInstance(project).REPLACE_LAMBDA_WITH_GREEK_LETTER = useGreekLambda
  }

  override def generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo =
    IntentionPreviewInfo.EMPTY
}

object KindProjectorUseCorrectLambdaKeywordInspection {
  val inspectionName: String = ScalaInspectionBundle.message("displayname.in.kind.projector.use.correct.lambda.keyword")
  val inspectionId: String = "KindProjectorUseCorrectLambdaKeyword"
  val codeStyleSettingUseGreekLambda: String = ScalaInspectionBundle.message("kind.projector.code.style.setting.use.lambda.char")
  val codeStyleSettingUseWordLambda: String = ScalaInspectionBundle.message("kind.projector.code.style.setting.use.lambda.word")
}
