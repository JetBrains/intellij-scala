package org.jetbrains.plugins.scala.worksheet

import com.intellij.ide.IdeView
import com.intellij.ide.scratch.ScratchFileCreationHelper
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.{PsiDirectory, PsiElement}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaFileImpl
import org.jetbrains.plugins.scala.worksheet.WorksheetScratchFileCreationHelper._
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetCommonSettings

// Scratch files do not belong to any module. But all over the place we extract scala language depending on module of a psi element.
// In order that Scala Worksheet detects language level we need to somehow "attach" a module to the worksheet.
// (the module which classpath is used to run the worksheet)
// This is the only extension point I found which allows to embed the process of creation of the scratch file.
// This HACKY solution abuses IdeView.selectElement which is called from ScratchFileActions.doCreateNewScratch
class WorksheetScratchFileCreationHelper extends ScratchFileCreationHelper {

  override def beforeCreate(project: Project, context: ScratchFileCreationHelper.Context): Unit = {
    super.beforeCreate(project, context)
    context.ideView = new WorksheetModulePatcherIdeView(Option(context.ideView))
  }
}

object WorksheetScratchFileCreationHelper {

  private class WorksheetModulePatcherIdeView(originalIdeView: Option[IdeView]) extends IdeView {

    override def selectElement(element: PsiElement): Unit = {
      originalIdeView.foreach(_.selectElement(element))
      element match {
        case file: ScalaFileImpl =>
          val module = WorksheetCommonSettings(file).getModuleFor
          file.putUserData(WORKSHEET_FILE_MODULE, module)
        case _ =>
      }
    }
    override def getDirectories: Array[PsiDirectory] = originalIdeView.fold(Array[PsiDirectory]())(_.getDirectories)
    override def getOrChooseDirectory(): PsiDirectory = originalIdeView.fold(null: PsiDirectory)(_.getOrChooseDirectory())
  }

  val WORKSHEET_FILE_MODULE = new Key[Module]("WorksheetFileModule")
}
