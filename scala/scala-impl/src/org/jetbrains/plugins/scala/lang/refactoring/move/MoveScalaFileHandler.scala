package org.jetbrains.plugins.scala.lang.refactoring.move

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Key
import com.intellij.psi._
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFileHandler
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.usageView.UsageInfo
import com.intellij.util.IncorrectOperationException
import org.jetbrains.annotations.{NotNull, Unmodifiable}
import org.jetbrains.plugins.scala.editor.importOptimizer.ScalaImportOptimizer
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScDeclaredElementsHolder
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.refactoring.Associations
import org.jetbrains.plugins.scala.statistics.ScalaRefactoringUsagesCollector

import java.util
import scala.jdk.CollectionConverters.IterableHasAsScala

class MoveScalaFileHandler extends MoveFileHandler {
  override def canProcessElement(element: PsiFile): Boolean = {
    if (!element.is[ScalaFile])
      return false
    val file = element.getVirtualFile
    if (file == null)
      return false

    val projectFileIndex = ProjectRootManager.getInstance(element.getProject).getFileIndex
    projectFileIndex.isInSource(file) || projectFileIndex.isInLibraryClasses(file)
  }

  override def prepareMovedFile(file: PsiFile, moveDestination: PsiDirectory, oldToNewMap: util.Map[PsiElement, PsiElement]): Unit = file match {
    case file: ScalaFile =>
      ScalaRefactoringUsagesCollector.logMoveFile(file.getProject)
      file.typeDefinitions.foreach(collectAssociations(_, file, withCompanion = false))
      file.putUserData(MoveScalaFileHandler.DefinedBeforeMove, MoveScalaFileHandler.topLevelPaths(file))

      moveFile(file, moveDestination, oldToNewMap)
    case _ =>
  }

  override def findUsages(psiFile: PsiFile, newParent: PsiDirectory, searchInComments: Boolean, searchInNonJavaFiles: Boolean): util.List[UsageInfo] =
    psiFile match {
      case scalaFile: ScalaFile =>
        val result = new util.ArrayList[UsageInfo]
        collectUsages(scalaFile, result, searchInComments, searchInNonJavaFiles)

        if (result.isEmpty) null else result
      case _ => null
    }

  override def retargetUsages(@NotNull @Unmodifiable usageInfos: util.List[? <: UsageInfo],
                              @NotNull oldToNewMap: util.Map[PsiElement, PsiElement]): Unit = {
    for (usage <- usageInfos.asScala) {
      usage match {
        case moveRenameUsage: MoveRenameUsageInfo =>
          val oldElement = moveRenameUsage.getReferencedElement
          val newElement = oldToNewMap.get(oldElement)
          val reference = moveRenameUsage.getReference
          if (reference != null) try {
            MoveScalaFileHandler.LOG.assertTrue(newElement != null, if (oldElement != null) oldElement else reference)
            reference.bindToElement(newElement)
          } catch {
            case ex: IncorrectOperationException =>
              MoveScalaFileHandler.LOG.error(ex)
          }
        case _ =>
      }
    }
  }

  @throws[IncorrectOperationException]
  override def updateMovedFile(file: PsiFile): Unit = file match {
    case file: ScalaFile =>
      // What the file itself defines moved with it: its references to those need no import from the old package.
      val movedAlong = Option(file.getUserData(MoveScalaFileHandler.DefinedBeforeMove)).getOrElse(Set.empty[String])
      file.putUserData(MoveScalaFileHandler.DefinedBeforeMove, null)
      file.typeDefinitions.foreach(applyWithCompanionModule(_, moveCompanion)(Associations.restoreFor(_, movedAlong)))

      new ScalaImportOptimizer().processFile(file).run()
    case _ =>
  }
}

object MoveScalaFileHandler {
  private val LOG = Logger.getInstance("#org.jetbrains.plugins.scala.lang.refactoring.move.MoveScalaFileHandler")

  /** The qualified names of a moved file's top-level definitions, as they were before the move. */
  private val DefinedBeforeMove: Key[Set[String]] = Key.create("MoveScalaFileHandler.DefinedBeforeMove")

  private def topLevelPaths(file: ScalaFile): Set[String] = {
    val names = file.typeDefinitions.map(_.name) ++ file.members.flatMap {
      case holder: ScDeclaredElementsHolder => holder.declaredNames
      case named: ScNamedElement => Seq(named.name)
      case _ => Seq.empty
    }
    val prefix = Option(file.getPackageName).filter(_.nonEmpty).map(_ + ".").getOrElse("")
    names.map(prefix + _).toSet
  }
}
