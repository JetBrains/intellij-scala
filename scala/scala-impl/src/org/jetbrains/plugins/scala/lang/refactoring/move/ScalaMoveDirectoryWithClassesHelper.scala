package org.jetbrains.plugins.scala
package lang
package refactoring
package move

import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.{FileTypeUtils, PsiTreeUtil}
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.move.moveClassesOrPackages.{MoveClassesOrPackagesUtil, MoveDirectoryWithClassesHelper}
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.usageView.UsageInfo
import com.intellij.util.Function
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, PsiElementExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject

import java.{util => ju}
import scala.annotation.nowarn
import scala.jdk.CollectionConverters._

class ScalaMoveDirectoryWithClassesHelper extends MoveDirectoryWithClassesHelper {

  override def findUsages(filesToMove: ju.Collection[PsiFile],
                          directoriesToMove: Array[PsiDirectory],
                          usages: ju.Collection[UsageInfo],
                          searchInComments: Boolean,
                          searchInNonJavaFiles: Boolean,
                          project: Project): Unit = {

    val packageNames = new ju.HashSet[String]
    filesToMove.forEach {
      case sf: ScalaFile =>
        val (packObj, classes) = sf.typeDefinitions.partition {
          case o: ScObject if o.isPackageObject => true
          case _ => false
        }

        for {
          aClass <- classes
          usage <- MoveClassesOrPackagesUtil.findUsages(aClass, searchInComments, searchInNonJavaFiles, aClass.name): @nowarn("cat=deprecation")
        } {
          usages.add(usage)
        }

        for {
          obj <- packObj
          named <- obj.namedElements
          ref <- ReferencesSearch.search(named).findAll().asScala
        } {
          val range = ref.getRangeInElement
          usages.add(new MoveRenameUsageInfo(ref.getElement, ref, range.getStartOffset, range.getEndOffset, named, false))
        }

        packageNames.add(packageName(sf))
      case _ =>
    }

    val psiFacade: JavaPsiFacade = JavaPsiFacade.getInstance(project)
    packageNames.forEach { packageName =>
      val aPackage: PsiPackage = psiFacade.findPackage(packageName)
      if (aPackage != null) {
        val remainsNothing: Boolean = aPackage.getDirectories.exists(!isUnderRefactoring(_, directoriesToMove))

        if (remainsNothing) {
          ReferencesSearch.search(aPackage, GlobalSearchScope.projectScope(project)).findAll().forEach { reference =>
            reference.getElement.parentOfType(classOf[ScImportStmt])
              .map(ImportStatementToRemoveUsage)
              .foreach(usages.add)
          }
        }
      }
    }
  }

  override def move(file: PsiFile,
                    moveDestination: PsiDirectory,
                    oldToNewElementsMapping: ju.Map[PsiElement, PsiElement],
                    movedFiles: ju.List[PsiFile],
                    listener: RefactoringElementListener): Boolean = {

    def moveClass(clazz: PsiClass): Unit = {
      clazz match {
        case o: ScObject if o.isPackageObject =>
          val oldElems = o.namedElements
          val newClass: PsiClass = MoveClassesOrPackagesUtil.doMoveClass(clazz, moveDestination)
          oldToNewElementsMapping.put(clazz, newClass)
          listener.elementMoved(newClass)

          val newElems = newClass.namedElements

          for {
            old <- oldElems
            newElem <- newElems.find(_.name == old.name)
          } {
            oldToNewElementsMapping.put(old, newElem)
            listener.elementMoved(newElem)
          }

        case _ =>
          val newClass: PsiClass = MoveClassesOrPackagesUtil.doMoveClass(clazz, moveDestination)
          oldToNewElementsMapping.put(clazz, newClass)
          listener.elementMoved(newClass)
      }


    }

    file match {
      case sf: ScalaFile if !FileTypeUtils.isInServerPageFile(file) =>
        sf.typeDefinitions.foreach(moveClass)
        true
      case _ => false
    }
  }

  override def postProcessUsages(usages: Array[UsageInfo], newDirMapper: Function[_ >: PsiDirectory, _ <: PsiDirectory]): Unit = {
    usages.foreach {
      case ImportStatementToRemoveUsage(impStmt) => impStmt.delete()
      case _ =>
    }
  }

  override def afterMove(newElement: PsiElement): Unit = beforeMove(newElement.getContainingFile)

  override def beforeMove(file: PsiFile): Unit = file match {
    case scalaFile: ScalaFile =>
      scalaFile.typeDefinitions.foreach {
        collectAssociations(_, scalaFile, withCompanion = false)
      }
    case _ =>
  }

  private def isUnderRefactoring(packageDirectory: PsiDirectory, directoriesToMove: Array[PsiDirectory]): Boolean = {
    directoriesToMove.exists(PsiTreeUtil.isAncestor(_, packageDirectory, true))
  }

  private def packageName(sf: ScalaFile) = {
    sf.typeDefinitions match {
      case Seq(obj: ScObject) if obj.isPackageObject =>
        if (obj.name == "`package`") obj.qualifiedName.stripSuffix(".`package`")
        else obj.qualifiedName
      case _ => sf.getPackageName
    }
  }
}

private case class ImportStatementToRemoveUsage(stmt: ScImportStmt) extends UsageInfo(stmt)
