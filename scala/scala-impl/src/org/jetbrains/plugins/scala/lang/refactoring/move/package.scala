package org.jetbrains.plugins.scala.lang.refactoring

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.roots.PackageIndex
import com.intellij.openapi.util.Key
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.FileTypeUtils
import com.intellij.psi.{JavaDirectoryService, PsiClass, PsiClassOwner, PsiDirectory, PsiDocumentManager, PsiElement, PsiFile, PsiNameIdentifierOwner, PsiNamedElement, PsiPackage, PsiReference}
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.move.moveClassesOrPackages.{MoveClassHandler, MoveClassesOrPackagesUtil}
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil
import com.intellij.refactoring.util.{MoveRenameUsageInfo, TextOccurrencesUtil}
import com.intellij.usageView.UsageInfo
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, PsiNamedElementExt, ScalaFileExt}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.{ScPackage, ScPackageLike, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.impl.ScPackageImpl
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

import java.{util => ju}
import scala.collection.mutable
import scala.jdk.CollectionConverters.IterableHasAsScala

package object move {

  object MoveDestination {

    private val key = Key.create[PsiDirectory]("MoveDestination")

    def apply(element: PsiElement): PsiDirectory = element.getUserData(key)

    def update(element: PsiElement, destination: PsiDirectory): Unit = {
      element.putUserData(key, destination)
    }
  }

  def collectAssociations(clazz: PsiClass,
                          file: ScalaFile,
                          withCompanion: Boolean): Unit =
    if (file.getContainingDirectory != MoveDestination(clazz)) {
      applyWithCompanionModule(clazz, withCompanion)(util.ScalaChangeContextUtil.encodeContextInfo)
    }

  def restoreAssociations(clazz: PsiClass): Unit =
    applyWithCompanionModule(clazz, moveCompanion)(Associations.restoreFor)

  def saveMoveDestination(element: PsiElement, moveDestination: PsiDirectory): Unit = {
    val classes = element match {
      case c: PsiClass => Seq(c)
      case f: ScalaFile => f.typeDefinitions
      case p: ScPackage => p.getClasses.toSeq
      case _ => Seq.empty
    }

    classes.foreach {
      applyWithCompanionModule(_, withCompanion = true) {
        MoveDestination(_) = moveDestination
      }
    }
  }

  def applyWithCompanionModule(clazz: PsiClass, withCompanion: Boolean)
                              (function: PsiClass => Unit): Unit =
    (Option(clazz) ++ companionModule(clazz, withCompanion)).foreach(function)

  def companionModule(clazz: PsiClass, withCompanion: Boolean): Option[ScTypeDefinition] =
    Option(clazz).collect {
      case definition: ScTypeDefinition if withCompanion => definition
    }.flatMap {
      _.baseCompanion
    }

  def moveCompanion: Boolean = ScalaApplicationSettings.getInstance.MOVE_COMPANION

  def moveFile(file: PsiFile, moveDestination: PsiDirectory,
               oldToNewMap: ju.Map[PsiElement, PsiElement],
               @Nullable listener: RefactoringElementListener = null): Boolean = {
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
      oldToNewMap.put(oldElement, newElement)
      if (listener != null) listener.elementMoved(newElement)

      if (handlePackageObject) {
        (oldElement, newElement) match {
          case (oldObj: ScObject, newObj: ScObject) if oldObj.isPackageObject =>
            movedElements(oldObj.namedElements, newObj.namedElements, handlePackageObject = false)
          case _ =>
        }
      }
    }

    /** Similar to [[MoveClassesOrPackagesUtil.doMoveClass]] but handles files without classes */
    def doMoveFile(scalaFile: ScalaFile): Unit = {
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
        doMoveFile(scalaFile)
        true
      case _ => false
    }
  }

  def collectUsages(file: ScalaFile, usages: ju.Collection[_ >: UsageInfo], searchInComments: Boolean, searchInNonJavaFiles: Boolean): Unit =
    file.namedElements.foreach {
      case obj: ScObject if obj.isPackageObject =>
        for {
          named <- obj.namedElements :+ obj
          usage <- findElementUsages(named, searchInComments, searchInNonJavaFiles)
        } usages.add(usage)
      case named =>
        findElementUsages(named, searchInComments, searchInNonJavaFiles)
          .foreach(usages.add)
    }

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
    if (qualifiedName != file.getPackageName && (ScalaNamesUtil.isQualifiedName(qualifiedName) || qualifiedName.isEmpty)) {
      file.setPackageName(qualifiedName)
    }
  }

  private[this] val LOG: Logger = Logger.getInstance(getClass)
}
