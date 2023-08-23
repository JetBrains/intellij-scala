package org.jetbrains.plugins.scala.lang.refactoring.move

import com.intellij.codeInsight.ChangeContextUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesUtil
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFileHandler
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.usageView.UsageInfo
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.scala.editor.importOptimizer.ScalaImportOptimizer
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.refactoring.Associations
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaChangeContextUtil.encodeContextInfo
import org.jetbrains.plugins.scala.statistics.ScalaRefactoringUsagesCollector

import java.util
import java.util.Collections
import scala.jdk.CollectionConverters.IterableHasAsScala

class MoveScalaFileHandler extends MoveFileHandler {
  override def canProcessElement(element: PsiFile): Boolean = {
    if (!element.is[ScalaFile])
      return false
    val file = element.getVirtualFile
    if (file == null)
      return false

    val projectFileIndex = ProjectRootManager.getInstance(element.getProject).getFileIndex
    !(projectFileIndex.isInSource(file) || projectFileIndex.isInLibraryClasses(file))
  }

  override def prepareMovedFile(file: PsiFile, moveDestination: PsiDirectory, oldToNewMap: util.Map[PsiElement, PsiElement]): Unit = {
    if (file.is[ScalaFile]) {
      ScalaRefactoringUsagesCollector.logMoveFile(file.getProject)
      ChangeContextUtil.encodeContextInfo(file, true)
      encodeContextInfo(file)
    }
  }

  override def findUsages(psiFile: PsiFile, newParent: PsiDirectory, searchInComments: Boolean, searchInNonJavaFiles: Boolean): util.List[UsageInfo] = {
    val result = new util.ArrayList[UsageInfo]

    psiFile match {
      case scalaFile: ScalaFile =>
        val newParentPackage = JavaDirectoryService.getInstance.getPackage(newParent)
        val qualifiedName = if (newParentPackage == null) ""
        else newParentPackage.getQualifiedName
        for (aClass <- scalaFile.getClasses) {
          val scope = GlobalSearchScope.projectScope(aClass.getProject)
          val fqn = StringUtil.getQualifiedName(qualifiedName, aClass.getName)
          val usages = MoveClassesOrPackagesUtil.findUsages(aClass, scope, searchInComments, searchInNonJavaFiles, fqn)
          Collections.addAll(result, usages: _*)
        }
      case _ =>
    }

    if (result.isEmpty) null
    else result
  }

  override def retargetUsages(usageInfos: util.List[UsageInfo], oldToNewMap: util.Map[PsiElement, PsiElement]): Unit = {
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
  override def updateMovedFile(file: PsiFile): Unit = {
    if (file.is[ScalaFile]) {
      ChangeContextUtil.decodeContextInfo(file, null, null)
      Associations.restoreFor(file)
      new ScalaImportOptimizer().processFile(file).run()
    }
  }
}

object MoveScalaFileHandler {
  private val LOG = Logger.getInstance("#org.jetbrains.plugins.scala.lang.refactoring.move.MoveScalaFileHandler")
}