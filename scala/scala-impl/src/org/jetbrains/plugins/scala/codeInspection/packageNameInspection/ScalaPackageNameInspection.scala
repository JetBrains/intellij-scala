package org.jetbrains.plugins.scala.codeInspection.packageNameInspection

import com.intellij.codeInspection._
import com.intellij.ide.projectView.impl.ProjectRootsUtil
import com.intellij.openapi.roots.{JavaProjectRootsUtil, ProjectRootManager}
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi._
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValueOrVariable
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.util.IntentionAvailabilityChecker

import scala.jdk.CollectionConverters._

// TODO Handle content and source roots that are files rather than directories
// TODO Handle nested content and source roots
class ScalaPackageNameInspection extends LocalInspectionTool {
  override def isEnabledByDefault: Boolean = true

  override def getID: String = ScalaPackageNameInspection.ToolId

  override def checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array[ProblemDescriptor] = {
    file match {
      case file: ScalaFile if IntentionAvailabilityChecker.checkInspection(this, file) =>
        if (file.isWorksheetFile) return null
        val members = file.members
        if (members.isEmpty) return null

        val projectFileIndex = ProjectRootManager.getInstance(file.getProject).getFileIndex

        val sourceFolder =
          for {
            virtualFile <- Option(file.getVirtualFile).flatMap(f => Option(f.getParent))
            root <- Option(projectFileIndex.getSourceRootForFile(virtualFile))
            sourceFolder <- Option(ProjectRootsUtil.getModuleSourceRoot(root, file.getProject))
          } yield sourceFolder

        val packagePrefix =
          for {
            sourceFolder <- sourceFolder
            packagePrefix = sourceFolder.getPackagePrefix if packagePrefix.nonEmpty
          } yield packagePrefix

        val dir = file.getContainingDirectory
        if (dir == null) return null
        // TODO Reuse sourceFolder to compute a relative path
        val packageNameByDir = packageNameFromFile(dir, packagePrefix) match {
          case Some(pack) => pack
          case None => return null
        }

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
            case td => td.topLevelQualifier.getOrElse("")
          }
          .distinct
        val packageQualifier = possiblePackageQualifiers match {
          case Seq(qualifier) => qualifier
          case _ =>
            // if the type definitions could be in multiple valid locations don't warn at all
            return null
        }

        def problemDescriptors(buffer: Seq[LocalQuickFix]): Seq[ProblemDescriptor] = {
          @Nls
          var message = ScalaInspectionBundle.message("package.names.does.not.correspond.to.directory.structure", packageQualifier, packageNameByDir)

          // Specifically make sure that the file path doesn't repeat an existing package prefix (twice).
          for (packagePrefix <- packagePrefix; sourceFolder <- sourceFolder if (packageNameByDir + ".").startsWith(packagePrefix + "." + packagePrefix + ".")) {
            message += "\n\n" + ScalaInspectionBundle.message("package.names.does.not.correspond.to.directory.structure.package.prefix", sourceFolder.getFile.getName, packagePrefix)
          }

          for (range <- ranges) yield
            manager.createProblemDescriptor(file, range, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly, buffer: _*)
        }

        if (packageNameByDir != packageQualifier) {
          assert(packageObjects.size <= 1, "There should only be one package object here... otherwise we should have already aborted")

          def renameQuickfix = new ScalaRenamePackageQuickFix(file, packageNameByDir)
          def moveQuickfix = new ScalaMoveToPackageQuickFix(file, packageQualifier)
          // the special root/empty-name package cannot have a package object
          val cannotRename = packageNameByDir == "" && packageObjects.nonEmpty
          val fixes =
            if (cannotRename) Seq(moveQuickfix)
            else Seq(renameQuickfix, moveQuickfix)

          problemDescriptors(fixes).toArray
        } else null
      case _ => null
    }
  }

  /**
   * stolen from [[com.intellij.core.CoreJavaFileManager.getPackage]]
   * unfortunately we need our own implementation... otherwise we cannot handle escaped package names at all
   */
  private def packageNameFromFile(file: PsiDirectory, packagePrefix: Option[String]): Option[String] = {
    val vFile = file.getVirtualFile
    // If there are nested source roots, prefer inner, SCL-20397
    // TODO There's no need to iterate over source roots of other modules
    val withoutPrefix = JavaProjectRootsUtil.getSuitableDestinationSourceRoots(file.getProject).asScala.sortBy(_.getPath.length).reverseIterator
      .flatMap { root =>
        if (VfsUtilCore.isAncestor(root, vFile, false)) {
          FileUtil.getRelativePath(root.getPath, vFile.getPath, '/')
            .toOption
            .map {
              case "." => ""
              case rel => rel.split('/').map(_.escapeNonIdentifiers).mkString(".")
            }
        } else None
      }
      .nextOption()

    withoutPrefix.map {
      case "" => packagePrefix.getOrElse("")
      case name => packagePrefix.fold("")(_ + ".") + name
    }
  }
}

object ScalaPackageNameInspection {
  val ToolId = "ScalaPackageName"
}