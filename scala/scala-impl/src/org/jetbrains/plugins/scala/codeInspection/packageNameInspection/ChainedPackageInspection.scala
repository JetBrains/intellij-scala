package org.jetbrains.plugins.scala
package codeInspection
package packageNameInspection

import com.intellij.codeInspection._
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

import scala.collection.JavaConverters._

class ChainedPackageInspection extends LocalInspectionTool {
  override def isEnabledByDefault = true

  override def getID = "ScalaChainedPackageClause"

  // TODO support multiple base packages simultaneously
  override def checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array[ProblemDescriptor] = {
    val problems = file.asOptionOf[ScalaFile].filter(!_.isScriptFile).flatMap { scalaFile =>

      scalaFile.getPackagings.headOption.flatMap { firstPackaging =>
        val basePackages = ScalaProjectSettings.getInstance(file.getProject).getBasePackages.asScala

        basePackages.find(basePackage => firstPackaging.packageName != basePackage
          && firstPackaging.packageName.startsWith(basePackage)).flatMap { basePackage =>

          firstPackaging.reference.map(_.getTextRange).map { range =>
            manager.createProblemDescriptor(file, range, "Package declaration could use chained package clauses",
              ProblemHighlightType.WEAK_WARNING, false, new UseChainedPackageQuickFix(scalaFile, basePackage))
          }
        }
      }
    }

    problems.toArray
  }
}

class UseChainedPackageQuickFix(myFile: ScalaFile, basePackage: String)
        extends AbstractFixOnPsiElement(s"Use chained package clauses: package $basePackage; package ...", myFile) {

  override protected def doApplyFix(file: ScalaFile)
                                   (implicit project: Project): Unit = {
    file.setPackageName(file.packageName)
  }

  override def getFamilyName = "Use chained package clauses"
}