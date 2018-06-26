package org.jetbrains.plugins.scala.worksheet.cell

import com.intellij.codeHighlighting.{Pass, TextEditorHighlightingPassRegistrar}
import com.intellij.codeInsight.daemon.impl.{DaemonCodeAnalyzerEx, TextEditorHighlightingPassRegistrarEx}
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.worksheet.actions.{CleanWorksheetAction, WorksheetFileHook}
import org.jetbrains.plugins.scala.worksheet.runconfiguration.WorksheetCache

import scala.collection.JavaConverters._

/**
  * User: Dmitry.Naydanov
  */
trait CellManager {
  def canHaveCells(file: PsiFile): Boolean
  def getCells(file: PsiFile): Iterable[CellDescriptor]
  def getCell(file: PsiFile, offset: Int): Option[CellDescriptor]

  def processProbablyStartElement(element: PsiElement): Boolean
  
  def isStartCell(element: PsiElement): Boolean

  def getCellFor(startElement: PsiElement): Option[CellDescriptor]
  def getNextCell(cellDescriptor: CellDescriptor): Option[CellDescriptor]
  def getPrevCell(cellDescriptor: CellDescriptor): Option[CellDescriptor]
  
  def clearAll(): Unit
  def clear(file: PsiFile): Unit
}

object CellManager {
  private lazy val manager = new BasicCellManager
  
  def getInstance(project: Project): CellManager = manager // todo 

  def deleteCells(file: PsiFile): Unit = {
    val project = file.getProject
    CellManager.getInstance(project).clear(file)
    
    rerunMarkerPass(file)
  }
  
  def installCells(file: PsiFile): Unit = {
    val project = file.getProject
    val vFile = file.getVirtualFile
    
    WorksheetFileHook.getEditorFrom(FileEditorManager.getInstance(project), vFile).foreach {
      editor => 
        WorksheetCache.getInstance(project).setLastProcessedIncremental(editor, None)
        CleanWorksheetAction.cleanAll(editor, vFile, project)
    }
    
    rerunMarkerPass(file)
  }
  
  private def rerunMarkerPass(file: PsiFile) {
    val project = file.getProject
    WorksheetFileHook.getDocumentFrom(FileEditorManager.getInstance(project), file.getVirtualFile).foreach {
      document =>
        DaemonCodeAnalyzerEx.getInstanceEx(project).restart(file)
        val fileStatusMap = DaemonCodeAnalyzerEx.getInstanceEx(project).getFileStatusMap
        fileStatusMap.markFileUpToDate(document, Pass.UPDATE_ALL)
        fileStatusMap.markFileUpToDate(document, Pass.EXTERNAL_TOOLS)
        fileStatusMap.markFileUpToDate(document, Pass.LOCAL_INSPECTIONS)
        val registrar = TextEditorHighlightingPassRegistrar.getInstance(project).asInstanceOf[TextEditorHighlightingPassRegistrarEx]
        registrar.getDirtyScopeTrackingFactories.asScala.foreach {
          factory => fileStatusMap.markFileUpToDate(document, factory.getPassId)
        }
    }
  }
}