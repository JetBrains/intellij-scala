package org.jetbrains.plugins.scala
package codeInspection
package packageNameInspection

import com.intellij.codeInspection._
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.{ProjectFileIndex, ProjectRootManager}
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator.isKeyword
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil.isBacktickedName
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
        if (file.isScriptFile) return null
        if (file.isWorksheetFile) return null
        val typeDefinitions = file.typeDefinitions
        if (typeDefinitions.isEmpty) return null

        val dir = file.getContainingDirectory
        if (dir == null) return null
        val pack = JavaDirectoryService.getInstance.getPackage(dir)
        if (pack == null) return null

        val ranges: Seq[TextRange] = file.packagingRanges match {
          case Seq() => typeDefinitions.map(_.nameId.getTextRange)
          case seq => seq
        }

        val possiblePackageQualifiers = typeDefinitions
          .map { td =>
            val qualName = Option(td.qualifiedName).getOrElse("")
            cleanKeywords(if (td.isPackageObject) qualName else parentQualifier(qualName))
          }
          .distinct
        val packageQualifier = possiblePackageQualifiers match {
          case Seq(qualifier) => qualifier
          case _ =>
            // if the type definitions could be in multiple valid locations don't warn at all
            return null
        }

        def problemDescriptors(buffer: Seq[LocalQuickFix]): Seq[ProblemDescriptor] = {
          var message = ScalaInspectionBundle.message("package.names.does.not.correspond.to.directory.structure", packageQualifier, pack.getQualifiedName)

          // Specifically make sure that the file path doesn't repeat an existing package prefix (twice).
          for (virtualFile <- Option(file.getVirtualFile);
               sourceFolder <- Option(ProjectRootManager.getInstance(file.getProject).getFileIndex.getSourceFolder(virtualFile));
               packagePrefix = sourceFolder.getPackagePrefix if packagePrefix.nonEmpty
               if (pack.getQualifiedName + ".").startsWith(packagePrefix + "." + packagePrefix + ".")) {
            message += "\n\n" + ScalaInspectionBundle.message("package.names.does.not.correspond.to.directory.structure.package.prefix", sourceFolder.getFile.getName, packagePrefix)
          }

          for (range <- ranges) yield
            manager.createProblemDescriptor(file, range, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly, buffer: _*)
        }

        if (pack.getQualifiedName != packageQualifier) {
          val fixes = Seq(
            new ScalaRenamePackageQuickFix(file, pack.getQualifiedName),
            new ScalaMoveToPackageQuickFix(file, packageQualifier))

          problemDescriptors(fixes).toArray
        } else null
      case _ => null
    }
  }

  private def parentQualifier(name: String): String = {
    val dot = name.lastIndexOf('.')
    if (dot >= 0) name.substring(0, dot) else ""
  }

  private def cleanKeywords(packageName: String): String = {
    packageName.split('.').map {
      case isBacktickedName(name) if isKeyword(name) => name
      case name => name
    }.mkString(".")
  }
}