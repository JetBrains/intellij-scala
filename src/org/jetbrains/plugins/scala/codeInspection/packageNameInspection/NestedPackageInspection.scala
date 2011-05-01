package org.jetbrains.plugins.scala
package codeInspection
package packageNameInspection

import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import collection.mutable.ArrayBuffer
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTypeDefinition, ScObject}
import fileNameInspection.{ScalaRenameFileQuickFix, ScalaRenameClassQuickFix}
import com.intellij.codeInspection._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import config.ScalaFacet
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.project.Project

class NestedPackageInspection extends LocalInspectionTool {
  def getGroupDisplayName: String = InspectionsUtil.SCALA

  def getDisplayName: String = "Nested Package Inspection"

  def getShortName: String = "Nested Package"

  override def isEnabledByDefault: Boolean = true

  override def getStaticDescription: String = "Inspection for files that do not use the base package configured in the Scala Facet"

  override def getID: String = "ScalaNestedPackage"

  override def checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array[ProblemDescriptor] = {
    val module = ScalaPsiUtil.getModule(file)
    val basePackage = ScalaFacet.findIn(module).flatMap(_.basePackage)
    (file, basePackage) match {
      case (sf: ScalaFile, Some(base)) if !sf.isScriptFile() =>
        sf.packagings.toList match {
          case p0 :: ps if p0.getPackageName != base && p0.getPackageName.startsWith(base) =>
            val fixes = Array[LocalQuickFix](new UseNestedPackageQuickFix(sf))
            val range: TextRange = sf.getPackagingRange
            val problem = manager.createProblemDescriptor(file, range, "Package declaration doesn't correspond to base package for module",
              ProblemHighlightType.WEAK_WARNING, false, fixes: _*)
            Array(problem)
          case _ => Array()
        }
      case _ => Array()
    }
  }
}

class UseNestedPackageQuickFix(file: ScalaFile) extends LocalQuickFix {
  def applyFix(project: Project, descriptor: ProblemDescriptor) {
    val packageName = file.packageName
    file.setPackageName(packageName)
  }

  def getFamilyName: String = "Use nested package declaration"

  def getName: String = getFamilyName
}