package org.jetbrains.plugins.scala
package worksheet.ui

import java.util

import com.intellij.openapi.editor.ex.FoldingListener
import com.intellij.openapi.editor.impl.{EditorImpl, FoldingModelImpl}
import com.intellij.openapi.editor.{Editor, FoldRegion}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.worksheet.processor.FileAttributeUtilCache
import org.jetbrains.plugins.scala.worksheet.ui.WorksheetFoldGroup.WorksheetFoldRegionListener

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
 * User: Dmitry.Naydanov
 * Date: 10.04.14.
 */
class WorksheetFoldGroup(private val viewerEditor: Editor, private val originalEditor: Editor, project: Project,
                         private val splitter: WorksheetDiffSplitters.SimpleWorksheetSplitter) {
  private val doc = originalEditor.getDocument
  private val regions = mutable.ArrayBuffer[FoldRegionInfo]()
  private val unfolded = new util.TreeMap[Int, Int]()

  def left2rightOffset(left: Int): Int = {
    val key: Int = unfolded floorKey left

    if (key == 0) left else {
      unfolded.get(key) + left
    }
  }
  
  def addRegion(foldingModel: FoldingModelImpl, start: Int, end: Int, leftStart: Int, 
                spaces: Int, leftSideLength: Int, isExpanded: Boolean) {
    val placeholder = viewerEditor.getDocument.getText(
      new TextRange(start, start + Math.min(end - start, WorksheetFoldGroup.PLACEHOLDER_LIMIT)))
    
    val region = foldingModel.createFoldRegion(start.toInt, end.toInt, placeholder, null, false)
    region.setExpanded(isExpanded)
    addRegion(region, leftStart.toInt, spaces.toInt, leftSideLength.toInt)

    foldingModel.addFoldRegion(region)
  }

  def addRegion(region: FoldRegion, start: Int, spaces: Int, leftSideLength: Int) {
    regions += FoldRegionInfo(region, region.isExpanded, start, spaces, leftSideLength)
  }

  def removeRegion(region: FoldRegion) {
    regions -= FoldRegionInfo(region, expanded = true, 0, 0, 0)
  }

  def onExpand(expandedRegion: FoldRegion): Boolean = {
    traverseAndChange(expandedRegion, expand = true)
  }

  def onCollapse(collapsedRegion: FoldRegion): Boolean = {
    traverseAndChange(collapsedRegion, expand = false)
  }

  def getCorrespondInfo: ArrayBuffer[(Int, Int, Int, Int, Int)] = regions map {
    case FoldRegionInfo(region: FoldRegion, _, leftStart, spaces, lsLength) =>
      (region.getStartOffset, region.getEndOffset, leftStart, spaces, lsLength)
  }
  
  def installOn(model: FoldingModelImpl) {
    model.addListener(new WorksheetFoldRegionListener(this), project) 
  }

  private def traverseAndChange(target: FoldRegion, expand: Boolean): Boolean = {
    if (!viewerEditor.asInstanceOf[EditorImpl].getContentComponent.hasFocus) return false

    val ((fromTo, offsetsSpaces), targetInfo) = traverseRegions(target) match {
      case (all, info, _) => (all.unzip, info)
    }

    if (targetInfo == null || targetInfo.expanded == expand) return false

    if (splitter != null) splitter.update(fromTo, offsetsSpaces)

    targetInfo.expanded = expand

    updateChangeFolded(targetInfo, expand)
    true
  }

  protected def serialize(): String = regions map {
    case FoldRegionInfo(region, expanded, trueStart, spaces, lsLength) =>
      s"${region.getStartOffset},${region.getEndOffset},$expanded,$trueStart,$spaces,$lsLength"
  } mkString "|"

  protected def deserialize(elem: String) {
    val folding = viewerEditor.getFoldingModel.asInstanceOf[FoldingModelImpl]

    folding runBatchFoldingOperation new Runnable {
      override def run() {
        elem split '|' foreach {
          regionElem =>
            regionElem split ',' match {
              case Array(start, end, expanded, leftStart, spaces, leftSideLength) =>
                try {
                  addRegion(folding, start.toInt, end.toInt, leftStart.toInt, spaces.toInt, leftSideLength.toInt, expanded.length == 4)
                } catch {
                  case _: NumberFormatException =>
                }
              case _ =>
            }
        }
      }
    }
  }

  private def offset2Line(offset: Int) = doc getLineNumber offset

  private def traverseRegions(target: FoldRegion): (mutable.Iterable[((Int, Int), (Int, Int))], FoldRegionInfo, Int) = {
    if (regions.isEmpty) return (mutable.ArrayBuffer.empty, null, 0)

    def numbers(reg: FoldRegionInfo, stored: Int) =
      ((offset2Line(reg.leftStart) - reg.lsLength, offset2Line(reg.leftStart)),
      (offset2Line(reg.leftStart) + stored, reg.spaces))

    ((mutable.ArrayBuffer[((Int, Int), (Int, Int))](), null: FoldRegionInfo, 0) /: regions) {
      case ((res, _, ff), reg) if reg.expanded && reg.region == target => (res, reg, ff)
      case ((res, _, ff), reg) if !reg.expanded && reg.region == target =>
        res append numbers(reg, ff)
        (res, reg, ff + reg.spaces)
      case ((res, a, ff), reg) if reg.expanded && reg.region != target =>
        res append numbers(reg, ff)
        (res, a, ff + reg.spaces)
      case (res, _) => res
    }
  }

  private def updateChangeFolded(target: FoldRegionInfo, expand: Boolean) {
    val line = offset2Line(target.leftStart)
    val key = unfolded floorKey line

    val spaces = target.spaces
    if (unfolded.get(key) == 0) {
      if (expand) unfolded.put(line, spaces) else unfolded remove line
      return
    }

    val lower = unfolded.tailMap(line).entrySet().iterator()
    while (lower.hasNext) {
      val t = lower.next()
      unfolded.put(t.getKey, if (expand) t.getValue + spaces else t.getValue - spaces)
    }

    if (expand) unfolded.put(line, unfolded.get(key) + spaces) else unfolded.remove(line)
  }


  private case class FoldRegionInfo(region: FoldRegion, var expanded: Boolean, leftStart: Int, spaces: Int, lsLength: Int) {
    override def equals(obj: scala.Any): Boolean = obj match {
      case info: FoldRegionInfo => this.region.equals(info.region)
      case _ => false
    }

    override def hashCode(): Int = region.hashCode()
  }
}

object WorksheetFoldGroup {
  private val PLACEHOLDER_LIMIT = 75
  private val WORKSHEET_PERSISTENT_FOLD_KEY = new FileAttribute("WorksheetPersistentFoldings", 1, false)
  
  private class WorksheetFoldRegionListener(val owner: WorksheetFoldGroup) extends FoldingListener {
    override def onFoldRegionStateChange(region: FoldRegion): Unit = 
      if (region.isExpanded) owner.onExpand(region) else owner.onCollapse(region)

    override def onFoldProcessingEnd() {}
  }

  def save(file: ScalaFile, group: WorksheetFoldGroup) {
    val virtualFile = file.getVirtualFile
    if (!virtualFile.isValid) return
    FileAttributeUtilCache.writeAttribute(WORKSHEET_PERSISTENT_FOLD_KEY, file, group.serialize())
  }

  def load(viewerEditor: Editor, originalEditor: Editor, project: Project,
           splitter: WorksheetDiffSplitters.SimpleWorksheetSplitter, file: PsiFile) {
    val bytes = FileAttributeUtilCache.readAttribute(WORKSHEET_PERSISTENT_FOLD_KEY, file)
    if (bytes == null) return

    lazy val group = new WorksheetFoldGroup(viewerEditor, originalEditor, project, splitter)
    bytes foreach {
      case nonEmpty if nonEmpty.length > 0 => group deserialize nonEmpty
      case _ =>
    }
  }
}
