package org.jetbrains.plugins.scala.worksheet.cell

import com.intellij.codeHighlighting.{Pass, TextEditorHighlightingPassRegistrar}
import com.intellij.codeInsight.daemon.impl.{DaemonCodeAnalyzerEx, TextEditorHighlightingPassRegistrarEx}
import com.intellij.openapi.command.{CommandProcessor, UndoConfirmationPolicy}
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiComment, PsiDocumentManager, PsiElement, PsiFile}
import org.jetbrains.plugins.scala.worksheet.actions.{CleanWorksheetAction, WorksheetFileHook}
import org.jetbrains.plugins.scala.worksheet.runconfiguration.WorksheetCache
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetExternalRunType
import org.jetbrains.plugins.scala.extensions._

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
  def canStartCell(element: PsiElement): Boolean

  def getCellFor(startElement: PsiElement): Option[CellDescriptor]
  def getNextCell(cellDescriptor: CellDescriptor): Option[CellDescriptor]
  def getPrevCell(cellDescriptor: CellDescriptor): Option[CellDescriptor]
  
  def clearAll(): Unit
  def clear(file: PsiFile): Unit

  protected def createCellDescriptor(comment: PsiComment, runType: WorksheetExternalRunType): CellDescriptor = {
    CellDescriptor(comment, runType, WorksheetCellExternalIdProvider.getSuitable(comment))
  }
}

object CellManager {
  val CELL_START_MARKUP = "//##"
  
  def getInstance(project: Project): CellManager = project.getComponent(classOf[BasicCellManager]) 

  def createCell(file: PsiFile, offset: Int, text: String = "\n") {
    modifyUnderCommand(file, "Create Cell", doc => {
      doc.insertString(offset, s"\n$CELL_START_MARKUP\n$text")
    })
  }
  
  def replaceCell(file: PsiFile, startOffset: Int, endOffset: Int, newText: String) {
    modifyUnderCommand(file, "Replace Cell", doc => {
      doc.replaceString(startOffset, endOffset, newText)
    })
  }
  
  def replaceCell(oldCell: CellDescriptor, newCellText: String) {
    for {
      element <- oldCell.getElement
      startOffset <- oldCell.getStartOffset
      endOffset <- oldCell.getEndOffset
    } replaceCell(element.getContainingFile, startOffset, endOffset, newCellText)
  }
  
  def replaceAll(file: PsiFile, newText: String) {
    replaceCell(file, 0, file.getTextLength, newText)
  }
  
  def removeCell(startElement: PsiElement) {
    val cellManager = getInstance(startElement.getProject)
    cellManager.getCellFor(startElement).foreach {
      descriptor => 
        val endOffset = cellManager.getNextCell(descriptor).flatMap(_.getElement) match {
          case Some(nextElement) => nextElement.getTextRange.getStartOffset
          case _ => startElement.getContainingFile.getTextLength
        }
        
        val startOffset = startElement.getTextRange.getStartOffset
        
        modifyUnderCommand(startElement.getContainingFile, "Remove Cell", doc => {
          doc.deleteString(startOffset, endOffset)
        })
    }
  }
  
  def deleteCells(file: PsiFile): Unit = {
    val project = file.getProject
    CellManager.getInstance(project).clear(file)
    
    rerunMarkerPass(file)
  }
  
  def installCells(file: PsiFile): Unit = {
    val project = file.getProject
    val vFile = file.getVirtualFile
    
    WorksheetFileHook.handleEditor(FileEditorManager.getInstance(project), vFile){
      editor =>
        WorksheetCache.getInstance(project).setLastProcessedIncremental(editor, None)
        CleanWorksheetAction.cleanAll(editor, vFile, project)
    }
    
    rerunMarkerPass(file)
  }
  
  private def modifyUnderCommand(file: PsiFile, title: String, action: Document => Unit) {
    val project = file.getProject
    val documentManager = PsiDocumentManager.getInstance(project)
    
    Option(documentManager.getDocument(file)).foreach {
      doc => 
        CommandProcessor.getInstance().executeCommand(
          project,
          new Runnable {
            override def run(): Unit = inWriteAction { 
              action(doc) 
              documentManager.commitDocument(doc)
            }
          }, title, null, UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION
        )
    }
  }
  
  private def rerunMarkerPass(file: PsiFile) {
    val project = file.getProject
    WorksheetFileHook.getDocumentFrom(project, file.getVirtualFile).foreach {
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