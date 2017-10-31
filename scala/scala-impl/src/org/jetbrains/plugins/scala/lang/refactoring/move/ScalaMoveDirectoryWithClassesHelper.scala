package org.jetbrains.plugins.scala.lang.refactoring.move

import java.util

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

import scala.collection.JavaConverters._

/**
 * @author Nikolay.Tropin
 */
class ScalaMoveDirectoryWithClassesHelper extends MoveDirectoryWithClassesHelper {

  override def findUsages(filesToMove: util.Collection[PsiFile],
                          directoriesToMove: Array[PsiDirectory],
                          usages: util.Collection[UsageInfo],
                          searchInComments: Boolean,
                          searchInNonJavaFiles: Boolean,
                          project: Project): Unit = {

    val packageNames: util.Set[String] = new util.HashSet[String]
    filesToMove.forEach {
      case sf: ScalaFile =>
        val (packObj, classes) = sf.typeDefinitions.partition {
          case o: ScObject if o.isPackageObject => true
          case _ => false
        }

        for {
          aClass <- classes
          usage <- MoveClassesOrPackagesUtil.findUsages(aClass, searchInComments, searchInNonJavaFiles, aClass.name)
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
                    oldToNewElementsMapping: util.Map[PsiElement, PsiElement],
                    movedFiles: util.List[PsiFile],
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

  override def postProcessUsages(usages: Array[UsageInfo], newDirMapper: Function[PsiDirectory, PsiDirectory]): Unit = {
    usages.foreach {
      case ImportStatementToRemoveUsage(impStmt) => impStmt.delete()
      case _ =>
    }
  }

  override def afterMove(newElement: PsiElement): Unit = {
    forClassesInFile(newElement.getContainingFile) { clazz =>
      ScalaMoveUtil.collectAssociations(clazz, withCompanion = false)
    }
  }

  override def beforeMove(psiFile: PsiFile): Unit = {
    forClassesInFile(psiFile) { clazz =>
      ScalaMoveUtil.collectAssociations(clazz, withCompanion = false)
    }
  }

  private def forClassesInFile(elem: PsiElement)(action: PsiClass => Unit): Unit = {
    elem.getContainingFile match {
      case sf: ScalaFile => sf.typeDefinitions.foreach(action)
      case _ =>
    }
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
