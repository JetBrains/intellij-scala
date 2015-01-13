package org.jetbrains.plugins.scala
package codeInspection
package packageNameInspection


import com.intellij.codeInspection._
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.util.IntentionAvailabilityChecker

/**
 * User: Alexander Podkhalyuzin
 * Date: 08.07.2009
 */

class ScalaPackageNameInspection extends LocalInspectionTool {
  override def isEnabledByDefault: Boolean = true

  override def getID: String = "ScalaPackageName"

  override def checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array[ProblemDescriptor] = {
    file match {
      case file: ScalaFile if IntentionAvailabilityChecker.checkInspection(this, file) =>
        if (file.isScriptFile()) return null
        if (file.typeDefinitions.length == 0) return null

        val dir = file.getContainingDirectory
        if (dir == null) return null
        val pack = JavaDirectoryService.getInstance().getPackage(dir)
        if (pack == null) return null

        val packName = cleanKeywords(file.packageName)
        val ranges: Seq[TextRange] = file.packagingRanges match {
          case Seq() => file.typeDefinitions.map(_.nameId.getTextRange)
          case seq => seq
        }

        def problemDescriptors(buffer: Seq[LocalQuickFix]): Seq[ProblemDescriptor] = ranges.map { range =>
          manager.createProblemDescriptor(file, range,
            "Package names doesn't correspond to directories structure, this may cause " +
                    "problems with resolve to classes from this file", ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            isOnTheFly, buffer: _*)
        }

        val expectedPackageName = file.typeDefinitions.head match {
          case obj: ScObject if obj.hasPackageKeyword =>
            Option(pack.getParentPackage).map(_.getQualifiedName).getOrElse("")
          case _ =>
            pack.getQualifiedName
        }

        if (packName == null) {
          val fixes = Seq(new EnablePerformanceProblemsQuickFix(file.getProject))
          problemDescriptors(fixes).toArray
        } else if (packName != expectedPackageName) {
          val fixes = Seq(
            new ScalaRenamePackageQuickFix(file, expectedPackageName),
            new ScalaMoveToPackageQuickFix(file, packName),
            new EnablePerformanceProblemsQuickFix(file.getProject))

          problemDescriptors(fixes).toArray
        } else null
      case _ => null
    }
  }

  private def cleanKeywords(packageName: String): String = {
    if (packageName == null) return null
    import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil._
    packageName.split('.').map {
      case isBacktickedName(name) if isKeyword(name) => name
      case name => name
    } mkString "."
  }
}