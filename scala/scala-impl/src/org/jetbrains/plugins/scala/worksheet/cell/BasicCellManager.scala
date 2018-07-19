package org.jetbrains.plugins.scala.worksheet.cell

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.{PsiComment, PsiElement, PsiFile, PsiWhiteSpace}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetFileSettings

import scala.collection.{mutable, _}

/**
  * User: Dmitry.Naydanov
  */
class BasicCellManager extends CellManager {
  private val cells = mutable.WeakHashMap.empty[PsiFile, mutable.TreeMap[Int, CellDescriptor]]

  override def canHaveCells(file: PsiFile): Boolean = file match {
    case scalaFile: ScalaFile if scalaFile.isWorksheetFile => 
      refreshMarkers(file)
      WorksheetFileSettings.getRunType(scalaFile).isUsesCell
    case _ => false
  }

  /**
    * Comment should be at top level and start at new line
    *
    * @return
    */
  override def isStartCell(element: PsiElement): Boolean = element match { //todo
    case comment: PsiComment =>
      refreshMarkers(element.getContainingFile)
      cells.get(comment.getContainingFile).exists(_.get(comment.getTextOffset).exists(compare(element)))
    case _ => false
  }

  override def getCellFor(startElement: PsiElement): Option[CellDescriptor] = {
    refreshMarkers(startElement.getContainingFile)
    cells.get(startElement.getContainingFile).flatMap(_.get(startElement.getTextOffset).filter(compare(startElement)))
  }

  override def processProbablyStartElement(element: PsiElement): Boolean = (element, element.getParent) match {
    case (comment: PsiComment, file: PsiFile) => checkPair(comment, file)
    case (comment: PsiComment, owner: PsiElement)
      if owner.getParent.isInstanceOf[PsiFile] && owner.getTextOffset == comment.getTextOffset =>
      checkPair(comment, owner.getContainingFile)
    case _ => false
  }

  override def getCells(file: PsiFile): Iterable[CellDescriptor] = cells.get(file).map(_.values).getOrElse(Seq.empty)

  override def getCell(file: PsiFile, offset: Int): Option[CellDescriptor] = {
    cells.get(file).flatMap(
      fileCells => fileCells.rangeImpl(Some(offset - 1), None).headOption.map(_._2)
    )
  }

  override def getNextCell(cellDescriptor: CellDescriptor): Option[CellDescriptor] = {
    getForRange(
      cellDescriptor,
      cellDescriptor.getElement.map(_.getTextOffset + BasicCellManager.CELL_START_MARKUP.length),
      None
    ).flatMap(_.headOption).map(_._2)
  }


  override def getPrevCell(cellDescriptor: CellDescriptor): Option[CellDescriptor] = {
    //todo
    getForRange(
      cellDescriptor,
      None,
      cellDescriptor.getElement.map(_.getTextOffset - 1)
    ).flatMap(_.lastOption).map(_._2)
  }

  override def clearAll(): Unit = {
    cells.clear()
  }

  override def clear(file: PsiFile): Unit = {
    cells.remove(file)
  }
  
  private def compare(element: PsiElement)(descriptor: CellDescriptor): Boolean = descriptor.getElement.contains(element)
  
  private def refreshMarkers(file: PsiFile): Unit = {
    if (!WorksheetFileSettings.getRunType(file).isUsesCell) cells.remove(file)
  }

  private def getSameFileCells(cellDescriptor: CellDescriptor): Option[mutable.TreeMap[Int, CellDescriptor]] =
    cellDescriptor.getElement.flatMap { element => cells.get(element.getContainingFile) }

  private def getForRange(cellDescriptor: CellDescriptor, start: Option[Int], end: Option[Int]) = {
    getSameFileCells(cellDescriptor).map(_.rangeImpl(start, end))
  }

  private def checkComment(comment: PsiComment): Boolean = comment.getText.startsWith(BasicCellManager.CELL_START_MARKUP)

  private def checkPair(comment: PsiComment, file: PsiFile): Boolean = canHaveCells(file) && checkComment(comment) && {
    def store(): Boolean = {
      val offset = comment.getTextOffset
      val runType = WorksheetFileSettings.getRunType(file)
      
      cells.get(file) match {
        case Some(fileCells) =>
          if (offset < fileCells.last._1) {
            fileCells.clear()
          }
          fileCells.put(offset, CellDescriptor(comment, runType))
        case _ =>
          cells.put(file, mutable.TreeMap((offset, CellDescriptor(comment, runType))))
      }

      true
    }

    val offset = comment.getTextRange.getStartOffset
    ((offset == 0) || (file.findElementAt(offset - 1) match {
      case ws: PsiWhiteSpace => ws.getTextOffset == 0 || StringUtil.containsLineBreak(ws.getText)
      case _ => false
    })) && store()
  }
}

object BasicCellManager {
  private val CELL_START_MARKUP = "//##"
}