package org.jetbrains.plugins.scala.lang.refactoring.move

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.roots.PackageIndex
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.{FileTypeUtils, PsiTreeUtil}
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.move.moveClassesOrPackages.{MoveClassHandler, MoveClassesOrPackagesUtil, MoveDirectoryWithClassesHelper}
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil
import com.intellij.refactoring.util.{MoveRenameUsageInfo, TextOccurrencesUtil}
import com.intellij.usageView.UsageInfo
import com.intellij.util.Function
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, PsiElementExt, PsiNamedElementExt, ScalaFileExt}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.api.{ScPackageLike, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.impl.ScPackageImpl
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.ScObjectImpl
import org.jetbrains.plugins.scala.lang.refactoring.move.ScalaMoveDirectoryWithClassesHelper.{LOG, findElementUsages, setPackageName}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

import java.{util => ju}
import scala.collection.mutable
import scala.jdk.CollectionConverters._

class ScalaMoveDirectoryWithClassesHelper extends MoveDirectoryWithClassesHelper {

  override def findUsages(filesToMove: ju.Collection[_ <: PsiFile],
                          directoriesToMove: Array[PsiDirectory],
                          usages: ju.Collection[_ >: UsageInfo],
                          searchInComments: Boolean,
                          searchInNonJavaFiles: Boolean,
                          project: Project): Unit = {
    val packageNames = new ju.HashSet[String]
    filesToMove.forEach {
      case scalaFile: ScalaFile =>
        scalaFile.namedElements.foreach {
          case obj: ScObject if obj.isPackageObject =>
            for {
              named <- obj.namedElements
              usage <- findElementUsages(named, searchInComments, searchInNonJavaFiles)
            } usages.add(usage)
          case named =>
            findElementUsages(named, searchInComments, searchInNonJavaFiles)
              .foreach(usages.add)
        }

        packageNames.add(packageName(scalaFile))
      case _ =>
    }

    val psiFacade: JavaPsiFacade = JavaPsiFacade.getInstance(project)
    packageNames.forEach { packageName =>
      val aPackage: PsiPackage = psiFacade.findPackage(packageName)
      if (aPackage != null) {
        val remainsNothing: Boolean = aPackage.getDirectories.forall(isUnderRefactoring(_, directoriesToMove))

        if (remainsNothing) {
          ReferencesSearch.search(aPackage, GlobalSearchScope.projectScope(project)).findAll().forEach { reference =>
            reference.getElement.parentOfType(classOf[ScImportExpr])
              .foreach {
                case expr if !isUnderRefactoring(expr, directoriesToMove) && expr.hasWildcardSelector =>
                  usages.add(ImportExpressionToRemoveUsage(expr))
                case _ =>
              }
          }
        }
      }
    }
  }

  override def move(file: PsiFile,
                    moveDestination: PsiDirectory,
                    oldToNewElementsMapping: ju.Map[PsiElement, PsiElement],
                    movedFiles: ju.List[_ >: PsiFile],
                    listener: RefactoringElementListener): Boolean = {

    def movedElements(oldElements: Seq[PsiNamedElement], newElements: Seq[PsiNamedElement], handlePackageObject: Boolean): Unit = {
      LOG.assertTrue(oldElements.sizeIs == newElements.size,
        s"Inconsistency in declaration count after move. Old file: $file, move destination: $moveDestination, old elements: $oldElements, new elements: $newElements")

      oldElements.zip(newElements).foreach { case (oldElement, newElement) =>
        movedElement(oldElement, newElement, handlePackageObject)
      }
    }

    /**
     * Update oldToNewElementsMapping. In case of a top-level package object, go through its members as well
     *
     * @param oldElement          element before move
     * @param newElement          corresponding element after move
     * @param handlePackageObject if true and element is a package object, also update mapping for its members
     */
    def movedElement(oldElement: PsiNamedElement, newElement: PsiNamedElement, handlePackageObject: Boolean): Unit = {
      oldToNewElementsMapping.put(oldElement, newElement)
      listener.elementMoved(newElement)

      if (handlePackageObject) {
        (oldElement, newElement) match {
          case (oldObj: ScObject, newObj: ScObject) if oldObj.isPackageObject =>
            movedElements(oldObj.namedElements, newObj.namedElements, handlePackageObject = false)
          case _ =>
        }
      }
    }

    /** Similar to [[MoveClassesOrPackagesUtil.doMoveClass]] but handles files without classes */
    def moveFile(scalaFile: ScalaFile): Unit = {
      val project = moveDestination.getProject
      val dstDir = moveDestination.getVirtualFile
      val pkgName = PackageIndex.getInstance(project).getPackageNameByDirectory(dstDir)
      val newPackage = ScPackageImpl.findPackage(project, pkgName)
      val oldNamedElements = scalaFile.namedElements

      MoveFilesOrDirectoriesUtil.doMoveFile(scalaFile, moveDestination)
      DumbService.getInstance(project).completeJustSubmittedTasks()
      val docManager = PsiDocumentManager.getInstance(project)
      val doc = docManager.getCachedDocument(scalaFile)
      if (doc != null) docManager.commitDocument(doc)
      moveDestination.findFile(scalaFile.name) match {
        case newFile: ScalaFile =>
          newPackage.foreach(setPackageName(newFile, _))
          val newNamedElements = newFile.namedElements
          movedElements(oldNamedElements, newNamedElements, handlePackageObject = true)
        case _ =>
          LOG.error(s"File $file is not as Scala file")
      }
    }

    file match {
      case scalaFile: ScalaFile if !FileTypeUtils.isInServerPageFile(file) =>
        moveFile(scalaFile)
        true
      case _ => false
    }
  }

  override def postProcessUsages(usages: Array[UsageInfo], newDirMapper: Function[_ >: PsiDirectory, _ <: PsiDirectory]): Unit = {
    usages.foreach {
      case ImportExpressionToRemoveUsage(expr) =>
        // delete with parent statement if it is the only expression
        expr.deleteExpr()
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

  private def isUnderRefactoring(element: PsiElement, directoriesToMove: Array[PsiDirectory]): Boolean =
    directoriesToMove.exists(PsiTreeUtil.isAncestor(_, element, /*strict*/ false))

  private def packageName(sf: ScalaFile) = {
    sf.typeDefinitions match {
      case Seq(obj: ScObject) if obj.isPackageObject =>
        if (obj.name == ScObjectImpl.LegacyPackageObjectNameInBackticks)
          ScObjectImpl.stripLegacyPackageObjectSuffixWithDot(obj.qualifiedName)
        else obj.qualifiedName
      case _ => sf.getPackageName
    }
  }
}

object ScalaMoveDirectoryWithClassesHelper {
  val LOG: Logger = Logger.getInstance(classOf[ScalaMoveDirectoryWithClassesHelper])

  /** Based on [[MoveClassesOrPackagesUtil.findUsages]], supports top-level definitions */
  private def findElementUsages(element: PsiNamedElement,
                                searchInStringsAndComments: Boolean,
                                searchInNonJavaFiles: Boolean): Array[UsageInfo] = {
    val newQName = element.name
    val searchScope = GlobalSearchScope.projectScope(element.getProject)
    val usages = new ju.ArrayList[UsageInfo]

    val foundRefs = mutable.HashSet.empty[PsiReference]
    for (ref <- ReferencesSearch.search(element, searchScope, false).asScala) {
      val range = ref.getRangeInElement
      if (!foundRefs(ref)) {
        usages.add(new MoveRenameUsageInfo(ref.getElement, ref, range.getStartOffset, range.getEndOffset, element, false))
        foundRefs += ref
      }
    }

    findNonCodeUsages(element, searchScope, searchInStringsAndComments, searchInNonJavaFiles, newQName, usages)
    MoveClassHandler.EP_NAME.getExtensionList.forEach(_.preprocessUsages(usages))

    usages.toArray(UsageInfo.EMPTY_ARRAY)
  }

  private def findNonCodeUsages(element: PsiElement,
                                searchScope: GlobalSearchScope,
                                searchInStringsAndComments: Boolean,
                                searchInNonJavaFiles: Boolean,
                                newQName: String,
                                usages: ju.Collection[_ >: UsageInfo]): Unit = {
    val stringToSearch = getStringToSearch(element)
    if (stringToSearch != null && stringToSearch.nonEmpty) {
      TextOccurrencesUtil.findNonCodeUsages(element, searchScope, stringToSearch,
        searchInStringsAndComments, searchInNonJavaFiles, newQName, usages)
    }
  }

  /** Same as [[MoveClassesOrPackagesUtil.getStringToSearch]] but handles top-level named elements like functions and variables */
  @Nullable
  private def getStringToSearch(element: PsiElement): String = element match {
    case pkg: PsiPackage => pkg.getQualifiedName
    case cls: PsiClass => cls.qualifiedName
    case dir: PsiDirectory =>
      val pkg = JavaDirectoryService.getInstance().getPackage(dir)
      if (pkg == null) null
      else pkg.getQualifiedName
    case file: PsiClassOwner => file.name
    case named: PsiNameIdentifierOwner => named.name
    case _ => null
  }

  private def setPackageName(file: ScalaFile, newPackage: ScPackageLike): Unit = {
    val qualifiedName = newPackage.fqn
    if (qualifiedName != file.getPackageName && ScalaNamesUtil.isQualifiedName(qualifiedName)) {
      file.setPackageName(qualifiedName)
    }
  }
}

private case class ImportExpressionToRemoveUsage(expr: ScImportExpr) extends UsageInfo(expr)
