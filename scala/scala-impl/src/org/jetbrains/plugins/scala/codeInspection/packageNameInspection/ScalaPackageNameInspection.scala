package org.jetbrains.plugins.scala
package codeInspection
package packageNameInspection

import com.intellij.codeInspection._
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValueOrVariable
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator.isKeyword
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil.isBacktickedName
import org.jetbrains.plugins.scala.util.IntentionAvailabilityChecker

class ScalaPackageNameInspection extends LocalInspectionTool {
  override def isEnabledByDefault: Boolean = true

  override def getID: String = "ScalaPackageName"

  override def checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array[ProblemDescriptor] = {
    file match {
      case file: ScalaFile if IntentionAvailabilityChecker.checkInspection(this, file) =>
        if (file.isScriptFile) return null
        if (file.isWorksheetFile) return null
        val members = file.members
        if (members.isEmpty) return null

        val dir = file.getContainingDirectory
        if (dir == null) return null
        val pack = JavaDirectoryService.getInstance.getPackage(dir)
        if (pack == null) return null

        lazy val packageObjects = members.collect { case td: ScTypeDefinition if td.isPackageObject => td }
        def ranges: Seq[TextRange] = file.packagingRanges match {
          case Seq() =>
            // if there is no packaging statement, we annotate the members directly
            // for this we only try to highlight the nameIds if possible
            members.collect {
              case named: ScNamedElement => Seq(named.nameId.getTextRange)
              case v: ScValueOrVariable => v.declaredElements.map(_.nameId.getTextRange)
              case e => Seq(e.getTextRange)
            }
            .flatten
          case seq => seq ++ packageObjects.map(_.nameId.getTextRange)
        }

        val possiblePackageQualifiers = members
          .map {
            case po: ScTypeDefinition if po.isPackageObject => po.qualifiedName
            case td => cleanKeywords(td.topLevelQualifier.getOrElse(""))
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
          assert(packageObjects.size <= 1, "There should only be one package object here... otherwise we should have already aborted")

          def renameQuickfix = new ScalaRenamePackageQuickFix(file, pack.getQualifiedName)
          def moveQuickfix = new ScalaMoveToPackageQuickFix(file, packageQualifier)
          // the special root/empty-name package cannot have a package object
          val cannotRename = pack.getQualifiedName == "" && packageObjects.nonEmpty
          val fixes =
            if (cannotRename) Seq(moveQuickfix)
            else Seq(renameQuickfix, moveQuickfix)

          problemDescriptors(fixes).toArray
        } else null
      case _ => null
    }
  }

  private def cleanKeywords(packageName: String): String = {
    packageName.split('.').map {
      case isBacktickedName(name) if isKeyword(name) => name
      case name => name
    }.mkString(".")
  }
}