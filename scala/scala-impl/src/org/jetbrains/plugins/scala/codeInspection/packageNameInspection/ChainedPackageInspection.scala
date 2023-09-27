package org.jetbrains.plugins.scala.codeInspection.packageNameInspection

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection._
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

class ChainedPackageInspection extends LocalInspectionTool {

  import ChainedPackageInspection._

  override def isEnabledByDefault = true

  override def getID = "ScalaChainedPackageClause"

  override def checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array[ProblemDescriptor] =
    file match {
      case file: ScalaFile =>
        val maybeProblemDescriptor = for {
          firstPackaging <- file.firstPackaging
          module <- file.module
          basePackage = ScalaProjectSettings.getInstance(file.getProject).getBasePackageFor(module)
          if !basePackage.isEmpty && firstPackaging.packageName != basePackage && (firstPackaging.packageName + ".").startsWith(basePackage + ".")
          reference <- firstPackaging.reference
          range = new TextRange(reference.getTextRange.getStartOffset, reference.getTextRange.getStartOffset + basePackage.length)
          quickFix = new UseChainedPackageQuickFix(file)
        } yield manager.createProblemDescriptor(
          file,
          range,
          ScalaInspectionBundle.message("package.declaration.could.use.chained.package.clauses", basePackage),
          ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
          false,
          quickFix
        )

        maybeProblemDescriptor match {
          case Some(problemDescriptor) => Array(problemDescriptor)
          case _ => ProblemDescriptor.EMPTY_ARRAY
        }
      case _ => ProblemDescriptor.EMPTY_ARRAY
    }
}

object ChainedPackageInspection {

  private class UseChainedPackageQuickFix(myFile: ScalaFile)
    extends AbstractFixOnPsiElement(ScalaInspectionBundle.message("use.chained.package.clauses.like"), myFile) {

    override protected def doApplyFix(file: ScalaFile)
                                     (implicit project: Project): Unit = {
      file.setPackageName(file.getPackageName)
    }

    override def generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo =
      IntentionPreviewInfo.EMPTY //TODO: SCL-21623

    override def getFamilyName: String = ScalaInspectionBundle.message("use.chained.package.clauses")
  }
}