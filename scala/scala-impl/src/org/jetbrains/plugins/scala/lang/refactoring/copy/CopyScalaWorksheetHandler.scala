package org.jetbrains.plugins.scala.lang.refactoring.copy

import com.intellij.psi.{PsiDirectory, PsiElement}
import com.intellij.refactoring.copy.{CopyFilesOrDirectoriesHandler, CopyHandlerDelegate, CopyHandlerDelegateBase}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.refactoring.copy.CopyScalaWorksheetHandler.{isScalaWorksheetFile, isSingleScalaWorksheetFile}

class CopyScalaWorksheetHandler extends CopyHandlerDelegateBase {

  //CopyFilesOrDirectoriesHandler is in the platform and can't be missing
  private lazy val copyFilesOrDirectoriesHandler: CopyHandlerDelegateBase =
    CopyHandlerDelegate.EP_NAME.getExtensions.find(_.is[CopyFilesOrDirectoriesHandler]).get.asInstanceOf[CopyHandlerDelegateBase]

  override def canCopy(elements: Array[PsiElement], fromUpdate: Boolean): Boolean = {
    if (isSingleScalaWorksheetFile(elements))
      copyFilesOrDirectoriesHandler.canCopy(elements, fromUpdate)
    else
      false
  }

  override def doCopy(elements: Array[PsiElement], defaultTargetDirectory0: PsiDirectory): Unit = {
    if (isSingleScalaWorksheetFile(elements)) {
      copyFilesOrDirectoriesHandler.doCopy(elements, defaultTargetDirectory0)
    }
  }


  override def doClone(element: PsiElement): Unit = {
    if (isScalaWorksheetFile(element)) {
      copyFilesOrDirectoriesHandler.doClone(element)
    }
  }
}

object CopyScalaWorksheetHandler {


  private def isSingleScalaWorksheetFile(elements: Array[PsiElement]): Boolean =
    elements.length == 1 && isScalaWorksheetFile(elements.head)

  private def isScalaWorksheetFile(element: PsiElement): Boolean = element match {
    case scalaFile: ScalaFile => scalaFile.isWorksheetFile
    case _ => false
  }
}
