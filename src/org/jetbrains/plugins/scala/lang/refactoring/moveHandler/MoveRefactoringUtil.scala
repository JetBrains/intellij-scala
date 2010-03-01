package org.jetbrains.plugins.scala
package lang
package refactoring
package moveHandler

import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiDirectory, JavaDirectoryService, PsiElement, PsiPackage}
import com.intellij.refactoring.move.MoveCallback
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesDialog
import com.intellij.refactoring.util.TextOccurrencesUtil
import com.intellij.refactoring.{HelpID, JavaRefactoringSettings}

/**
 * User: Alexander Podkhalyuzin
 * Date: 30.12.2008
 */

object MoveRefactoringUtil {
  def moveClass(project: Project, elements: Array[PsiElement], initialTargetElement: PsiElement, moveCallback: MoveCallback) {
    //todo: add checking elements
    def isSearchTextOccurences: Boolean = {
      for (element <- elements if TextOccurrencesUtil.isSearchTextOccurencesEnabled(element)) return true
      false
    }
    val searchTextOccurences = isSearchTextOccurences
    val initialTargetPackageName = getInitialTargetPackageName(initialTargetElement, elements)
    val initialTargetDirectory = getInitialTargetDirectory(initialTargetElement, elements)
    val isTargetDirectoryFixed = getContainerDirectory(initialTargetElement) != null;
    val moveDialog =
      new MoveClassesOrPackagesDialog(project, searchTextOccurences, elements, initialTargetElement, moveCallback)
    val searchForTextOccurences = JavaRefactoringSettings.getInstance().MOVE_SEARCH_FOR_TEXT
    val searchInComments = JavaRefactoringSettings.getInstance().MOVE_SEARCH_IN_COMMENTS
    moveDialog.setData(elements, initialTargetPackageName, initialTargetDirectory, isTargetDirectoryFixed, searchInComments,
                       searchForTextOccurences, HelpID.getMoveHelpID(elements(0)))
    try {
      moveDialog.show()
    }
    catch {
      case e: Exception => //todo: write right Move Refactoring!
    }
  }

  private def getInitialTargetPackageName(initialTargetElement: PsiElement, movedElements: Array[PsiElement]): String = {
    var name = getContainerPackageName(initialTargetElement);
    if (name == null) {
      if (movedElements != null) {
        name = getTargetPackageNameForMovedElement(movedElements(0))
      }
      if (name == null) {
        val commonDirectory = getCommonDirectory(movedElements)
        if (commonDirectory != null && JavaDirectoryService.getInstance().getPackage(commonDirectory) != null) {
          name = JavaDirectoryService.getInstance().getPackage(commonDirectory).getQualifiedName()
        }
      }
    }
    if (name == null) {
      name = ""
    }
    return name
  }

  private def getTargetPackageNameForMovedElement(psiElement: PsiElement): String = {
    psiElement match {
      case psiPackage: PsiPackage => {
        val parent = psiPackage.getParentPackage
        if (parent != null) parent.getQualifiedName else ""
      }
      case x: PsiDirectory => {
        val aPackage = JavaDirectoryService.getInstance().getPackage(x);
        if  (aPackage != null) getTargetPackageNameForMovedElement(aPackage) else "";
      }
      case null => null
      case _ => {
        val aPackage = JavaDirectoryService.getInstance().getPackage(psiElement.getContainingFile().getContainingDirectory());
        if (aPackage != null) aPackage.getQualifiedName() else "";
      }
    }
  }

  private def getContainerPackageName(psiElement: PsiElement): String = {
    psiElement match {
      case x: PsiPackage => x.getQualifiedName
      case x: PsiDirectory => {
        val aPackage = JavaDirectoryService.getInstance().getPackage(x)
        return if (aPackage != null) aPackage.getQualifiedName() else ""
      }
      case null => return null
      case x => {
        val aPackage = JavaDirectoryService.getInstance().getPackage(x.getContainingFile().getContainingDirectory())
        return if(aPackage != null) aPackage.getQualifiedName() else ""
      }
    }
  }

  private def getInitialTargetDirectory(initialTargetElement: PsiElement, movedElements: Array[PsiElement]): PsiDirectory = {
    getContainerDirectory(initialTargetElement) match {
      case null => {
        if (movedElements != null) {
          getCommonDirectory(movedElements) match {
            case x if x != null => x
            case _ => getContainerDirectory(movedElements(0));
          }
        } else null
      }
      case x => x
    }
  }

  private def getCommonDirectory(movedElements: Array[PsiElement]): PsiDirectory = {
    var commonDirectory: PsiDirectory = null

    for (movedElement <- movedElements) {
      val containingFile = movedElement.getContainingFile();
      if (containingFile != null) {
        val containingDirectory = containingFile.getContainingDirectory();
        if (containingDirectory != null) {
          if (commonDirectory == null) {
            commonDirectory = containingDirectory;
          }
          else {
            if (commonDirectory != containingDirectory) {
              return null;
            }
          }
        }
      }
    }
    return commonDirectory
  }


  private def getContainerDirectory(psiElement: PsiElement): PsiDirectory = {
    psiElement match {
      case _: PsiPackage => null
      case x: PsiDirectory => x
      case x: PsiElement => x.getContainingFile.getContainingDirectory
      case null => null
    }
  }
}