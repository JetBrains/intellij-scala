package org.jetbrains.plugins.scala
package codeInspection
package packageNameInspection


import lang.psi.api.ScalaFile
import com.intellij.codeInspection._

import java.lang.String
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import codeStyle.CodeStyleSettingsManager
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import com.intellij.openapi.module.{ModuleUtil, Module}
import config.ScalaFacet
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings

/**
 * User: Alexander Podkhalyuzin
 * Date: 08.07.2009
 */

class PackageNameInspection extends LocalInspectionTool {
  def getGroupDisplayName: String = InspectionsUtil.SCALA

  def getDisplayName: String = "Package Name Inspection"

  def getShortName: String = "ScalaPackageName"

  override def isEnabledByDefault: Boolean = true

  override def getStaticDescription: String = "Inspection for files with package statement which does not correspond to package structure"

  override def getID: String = "ScalaPackageName"

  override def checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array[ProblemDescriptor] = {
    file match {
      case file: ScalaFile => {
        if (file.isScriptFile()) return null
        if (file.getClasses.length == 0) return null

        val dir = file.getContainingDirectory
        if (dir == null) return null
        val pack = JavaDirectoryService.getInstance().getPackage(dir)
        if (pack == null) return null

        val packName = file.packageName
        val range: TextRange = file.getPackagingRange

        def problemDescriptor(buffer: Seq[LocalQuickFix]): ProblemDescriptor =
          manager.createProblemDescriptor(file, range,
            "Package names doesn't correspond to directories structure, this may cause " +
                    "problems with resolve to classes from this file", ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            isOnTheFly, buffer: _*)

        val settings = CodeStyleSettingsManager.getSettings(file.getProject).
          getCustomSettings(classOf[ScalaCodeStyleSettings])

        val module: Module = ModuleUtil.findModuleForPsiElement(file)
        val prefix = if (module != null && settings.IGNORE_PERFORMANCE_TO_FIND_ALL_CLASS_NAMES) {
          ScalaFacet.findIn(module).flatMap(f => f.basePackage).getOrElse("")
        } else ""
        val expectedFilePackageName = file.getClasses.head match {
          case obj: ScObject if obj.hasPackageKeyword =>
            Option(pack.getParentPackage).map(_.getQualifiedName).getOrElse("")
          case _ =>
            pack.getQualifiedName
        }
        val expectedPackageName = (if (prefix != "") prefix + "." else "") + expectedFilePackageName

        if (packName == null) {
          val fixes = Seq(new EnablePerformanceProblemsQuickFix(file.getProject))
          Array(problemDescriptor(fixes))
        } else if (packName != expectedPackageName) {
          val fixes = Seq(
            new ScalaRenamePackageQuickFix(file, expectedPackageName),
            new ScalaMoveToPackageQuickFix(file, packName),
            new EnablePerformanceProblemsQuickFix(file.getProject))
          
          Array(problemDescriptor(fixes))
        } else null
      }
      case _ => null
    }
  }
}