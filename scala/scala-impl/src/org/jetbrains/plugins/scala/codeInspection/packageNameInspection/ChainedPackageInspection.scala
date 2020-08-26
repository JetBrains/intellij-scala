package org.jetbrains.plugins.scala
package codeInspection
package packageNameInspection

import com.intellij.codeInspection._
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

import scala.jdk.CollectionConverters._

class ChainedPackageInspection extends LocalInspectionTool {

  import ChainedPackageInspection._

  override def isEnabledByDefault = true

  override def getID = "ScalaChainedPackageClause"

  // TODO support multiple base packages simultaneously
  override def checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array[ProblemDescriptor] =
    file match {
      case file: ScalaFile if !file.isScriptFile =>
        val maybeProblemDescriptor = for {
          firstPackaging <- file.firstPackaging

          basePackage <- findBasePackageByName(firstPackaging.packageName)(file.getProject)
          reference <- firstPackaging.reference
          range = reference.getTextRange
          quickFix = new UseChainedPackageQuickFix(file, basePackage)
        } yield manager.createProblemDescriptor(
          file,
          range,
          ScalaInspectionBundle.message("package.declaration.could.use.chained.package.clauses"),
          ProblemHighlightType.WEAK_WARNING,
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

  private class UseChainedPackageQuickFix(myFile: ScalaFile, basePackage: String)
    extends AbstractFixOnPsiElement(ScalaInspectionBundle.message("use.chained.package.clauses.like", basePackage), myFile) {

    override protected def doApplyFix(file: ScalaFile)
                                     (implicit project: Project): Unit = {
      file.setPackageName(file.getPackageName)
    }

    override def getFamilyName: String = ScalaInspectionBundle.message("use.chained.package.clauses")
  }

  private def findBasePackageByName(packageName: String)
                                   (implicit project: Project) = {
    val packages = ScalaProjectSettings.getInstance(project).getBasePackages.asScala
    packages.find { basePackage =>
      basePackage != packageName && packageName.startsWith(basePackage)
    }
  }
}