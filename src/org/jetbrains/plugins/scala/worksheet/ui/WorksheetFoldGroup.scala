package org.jetbrains.plugins.scala
package worksheet.ui

import java.util

import com.intellij.openapi.editor.impl.{EditorImpl, FoldingModelImpl}
import com.intellij.openapi.editor.{Editor, FoldRegion}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.worksheet.processor.FileAttributeUtilCache

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

  def left2rightOffset(left: Int) = {
    val key: Int = unfolded floorKey left

    if (key == 0) left else {
      unfolded.get(key) + left
    }
  }

  def addRegion(region: WorksheetFoldRegionDelegate, start: Int, spaces: Int, leftSideLength: Int) {
    regions += FoldRegionInfo(region, region.isExpanded, start, spaces, leftSideLength)
  }

  def removeRegion(region: WorksheetFoldRegionDelegate) {
    regions -= FoldRegionInfo(region, expanded = true, 0, 0, 0)
  }

  def onExpand(expandedRegion: WorksheetFoldRegionDelegate): Boolean = {
    traverseAndChange(expandedRegion, expand = true)
  }

  def onCollapse(collapsedRegion: WorksheetFoldRegionDelegate): Boolean = {
    traverseAndChange(collapsedRegion, expand = false)
  }

  def getCorrespondInfo = regions map {
    case FoldRegionInfo(region: WorksheetFoldRegionDelegate, _, leftStart, spaces, lsLength) =>
      (region.getStartOffset, region.getEndOffset, leftStart, spaces, lsLength)
  }

  private def traverseAndChange(target: WorksheetFoldRegionDelegate, expand: Boolean): Boolean = {
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

  protected def serialize() = regions map {
    case FoldRegionInfo(region, expanded, trueStart, spaces, lsLength) =>
      s"${region.getStartOffset},${region.getEndOffset},$expanded,$trueStart,$spaces,$lsLength"
  } mkString "|"

  protected def deserialize(elem: String) {
    val folding = viewerEditor.getFoldingModel.asInstanceOf[FoldingModelImpl]

    folding runBatchFoldingOperation new Runnable {
      override def run() {
        elem split '|' foreach {
          case regionElem =>
            regionElem split ',' match {
              case Array(start, end, expanded, trueStart, spaces, lsLength) =>
                try {
                  val region = new WorksheetFoldRegionDelegate (
                    viewerEditor,
                    start.toInt,
                    end.toInt,
                    trueStart.toInt,
                    spaces.toInt,
                    WorksheetFoldGroup.this,
                    lsLength.toInt
                  )

                  region.setExpanded(expanded.length == 4)

                  folding addFoldRegion region
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

  private def traverseRegions(target: WorksheetFoldRegionDelegate): (mutable.Iterable[((Int, Int), (Int, Int))], FoldRegionInfo, Int) = {
    if (regions.isEmpty) return (mutable.ArrayBuffer.empty, null, 0)

    def numbers(reg: FoldRegionInfo, stored: Int) =
      ((offset2Line(reg.trueStart) - reg.lsLength, offset2Line(reg.trueStart)),
      (offset2Line(reg.trueStart) + stored, reg.spaces))

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
    val line = offset2Line(target.trueStart)
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


  private case class FoldRegionInfo(region: FoldRegion, var expanded: Boolean, trueStart: Int, spaces: Int, lsLength: Int) {
    override def equals(obj: scala.Any): Boolean = obj match {
      case info: FoldRegionInfo => this.region.equals(info.region)
      case _ => false
    }

    override def hashCode(): Int = region.hashCode()
  }
}

object WorksheetFoldGroup {
  private val WORKSHEET_PERSISTENT_FOLD_KEY = new FileAttribute("WorksheetPersistentFoldings", 1, false)

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
