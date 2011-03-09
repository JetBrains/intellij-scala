package org.jetbrains.plugins.scala
package codeInspection
package packageNameInspection


import lang.psi.api.ScalaFile
import collection.mutable.ArrayBuffer
import com.intellij.codeInspection._

import java.lang.String
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScClass}

/**
 * User: Alexander Podkhalyuzin
 * Date: 08.07.2009
 */

class PackageNameInspection extends LocalInspectionTool {
  def getGroupDisplayName: String = InspectionsUtil.SCALA

  def getDisplayName: String = "Package Name Inspection"

  def getShortName: String = "Package Name"

  override def isEnabledByDefault: Boolean = true

  override def getStaticDescription: String = "Inspection for files with package statement which does not correspond to package structure"

  override def getID: String = "PackageName"

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
        val range: TextRange = getPackagingRange(file)

        def problemDescriptor(buffer: Seq[LocalQuickFix]): ProblemDescriptor =
          manager.createProblemDescriptor(file, range,
            "Package names doesn't correspond to directories structure, this may cause " +
                    "problems with resolve to classes from this file", ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            isOnTheFly, buffer: _*)

        val expectedPackageName = file.getClasses.head match {
          case obj: ScObject if obj.hasPackageKeyword =>
            Option(pack.getParentPackage).map(_.getQualifiedName).getOrElse("")
          case _ =>
            pack.getQualifiedName
        }

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

  private def getPackagingRange(file: ScalaFile): TextRange = {
    def getRange: TextRange = {
      new TextRange(0, file.getText.indexOf('\n') match {
        case x if x < 0 => file.getText.length
        case y => y
      })
    }
    file.getPackagings.toList match {
      case Nil => getRange
      case h :: t => h.reference match {
        case Some(ref) => ref.getTextRange
        case _ => getRange
      }
    }
  }
}