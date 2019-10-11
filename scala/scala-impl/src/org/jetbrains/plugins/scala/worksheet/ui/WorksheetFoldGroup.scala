package org.jetbrains.plugins.scala
package worksheet.ui

import java.util

import com.intellij.openapi.editor.ex.{FoldingListener, FoldingModelEx}
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.{Document, Editor, FoldRegion}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.worksheet.processor.FileAttributeUtilCache
import org.jetbrains.plugins.scala.worksheet.ui.WorksheetFoldGroup._

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

final class WorksheetFoldGroup(
  private val viewerEditor: Editor, // left editor
  private val originalEditor: Editor, // right editor
  project: Project,
  private val splitter: WorksheetDiffSplitters.SimpleWorksheetSplitter
) {

  import FoldRegionSerializer._

  private val originalDocument: Document = originalEditor.getDocument
  private val viewerDocument  : Document = viewerEditor.getDocument
  
  private val _regions = mutable.ArrayBuffer[FoldRegionInfo]()
  private val unfolded = new util.TreeMap[Int, Int]()

  def regions: Seq[FoldRegionInfo] = _regions

  def left2rightOffset(left: Int): Int = {
    val key: Int = unfolded floorKey left

    if (key == 0) {
      left
    } else {
      left + unfolded.get(key)
    }
  }

  /**
   * @param rightStartOffset start of the range to fold in the viewerEditor
   * @param rightEndOffset   end of the range to fold in the viewerEditor
   * @param leftEndOffset    end of the current input content from the  originalEditor
   * @param leftContentLines number of lines of the current input content from the originalEditor
   * @param spaces           number of folded lines - 1 in the viewerEditor (number of new line characters folded)
   * @param isExpanded       whether the region should be expanded right after folding
   */
  def addRegion(foldingModel: FoldingModelEx)
               (rightStartOffset: Int, rightEndOffset: Int,
                leftEndOffset: Int,
                leftContentLines: Int,
                spaces: Int,
                isExpanded: Boolean): Unit = {
    val placeholder: String = {
      val offset = Math.min(rightEndOffset - rightStartOffset, WorksheetFoldGroup.PLACEHOLDER_LIMIT)
      val range = new TextRange(rightStartOffset, rightStartOffset + offset)
      viewerDocument.getText(range)
    }

    val region = foldingModel.createFoldRegion(rightStartOffset, rightEndOffset, placeholder, null, false)
    if (region == null) return //something went wrong

    region.setExpanded(isExpanded)
    addRegion(region, leftEndOffset, spaces, leftContentLines)
  }

  private def addRegion(region: FoldRegion, start: Int, spaces: Int, leftSideLength: Int): Unit =
    _regions += FoldRegionInfo(region, region.isExpanded, start, spaces, leftSideLength)

  private def addParsedRegions(regions: Seq[ParsedRegion]): Unit = {
    val folding = viewerEditor.getFoldingModel.asInstanceOf[FoldingModelEx]
    folding.runBatchFoldingOperation { () =>
      regions.foreach(addParsedRegion(folding, _))
    }
  }

  private def addParsedRegion(folding: FoldingModelEx, region: ParsedRegion): Unit = {
    val ParsedRegion(start, end, expanded, leftStart, spaces, leftSideLength) = region
    addRegion(folding)(start, end, leftStart, leftSideLength, spaces, expanded)
  }

  private def onExpand(expandedRegion: FoldRegion): Boolean =
    traverseAndChange(expandedRegion, expand = true)

  private def onCollapse(collapsedRegion: FoldRegion): Boolean =
    traverseAndChange(collapsedRegion, expand = false)

  def installOn(model: FoldingModelEx): Unit =
    model.addListener(new WorksheetFoldRegionListener(this), project)

  private def traverseAndChange(target: FoldRegion, expand: Boolean): Boolean = {
    if (!viewerEditor.asInstanceOf[EditorImpl].getContentComponent.hasFocus) return false

    val ((fromTo, offsetsSpaces), targetInfo) = traverseRegions(target) match {
      case (all, info, _) => (all.unzip, info)
    }

    if (targetInfo == null || targetInfo.expanded == expand) return false

    if (splitter != null) {
      splitter.update(fromTo, offsetsSpaces)
    }

    targetInfo.expanded = expand

    updateChangeFolded(targetInfo, expand)
    true
  }

  private def offset2Line(offset: Int) = originalDocument.getLineNumber(offset)

  /** @return TODO: ??? */
  private def traverseRegions(target: FoldRegion): (Iterable[((Int, Int), (Int, Int))], FoldRegionInfo, Int) = {
    val emptyResult: (Seq[((Int, Int), (Int, Int))], FoldRegionInfo, Int) = (Seq.empty, null, 0)
    if (_regions.isEmpty) return emptyResult

    def numbers(reg: FoldRegionInfo, stored: Int): ((Int, Int), (Int, Int)) = {
      val first = (offset2Line(reg.leftStart) - reg.leftSideLength, offset2Line(reg.leftStart))
      val second = (offset2Line(reg.leftStart) + stored, reg.spaces)
      (first, second)
    }

    val result = _regions.foldLeft(emptyResult) { case (acc@(res, currentRegion, offset), nextRegion) =>
      val accNew = if (nextRegion.region == target) {
        if (nextRegion.expanded) {
          (res, nextRegion, offset)
        } else {
          val resUpdated = res :+ numbers(nextRegion, offset)
          (resUpdated, nextRegion, offset + nextRegion.spaces)
        }
      } else if (nextRegion.expanded) {
        val resUpdated = res :+ numbers(nextRegion, offset)
        (resUpdated, currentRegion, offset + nextRegion.spaces)
      } else {
        acc
      }
      accNew
    }
    result
  }

  private def updateChangeFolded(target: FoldRegionInfo, expand: Boolean) {
    val line = offset2Line(target.leftStart)
    val key = unfolded floorKey line

    val spaces = target.spaces
    if (unfolded.get(key) == 0) {
      if (expand) unfolded.put(line, spaces)
      else unfolded.remove(line)
      return
    }

    val lower = unfolded.tailMap(line).entrySet().iterator()
    while (lower.hasNext) {
      val t = lower.next()
      val magicValue = if (expand) t.getValue + spaces else t.getValue - spaces
      unfolded.put(t.getKey, magicValue)
    }

    if (expand) {
      unfolded.put(line, unfolded.get(key) + spaces)
    } else {
      unfolded.remove(line)
    }
  }
}

object WorksheetFoldGroup {

  import FoldRegionSerializer._

  private val PLACEHOLDER_LIMIT = 75
  private val WORKSHEET_PERSISTENT_FOLD_KEY = new FileAttribute("WorksheetPersistentFoldings", 1, false)

  // TODO: why the hell is this a `case` class?
  case class FoldRegionInfo(region: FoldRegion,
                            var expanded: Boolean,
                            leftStart: Int,
                            spaces: Int,
                            leftSideLength: Int) {
    override def equals(obj: scala.Any): Boolean = obj match {
      case info: FoldRegionInfo => this.region.equals(info.region)
      case _ => false
    }

    override def hashCode(): Int = region.hashCode()
  }

  private class WorksheetFoldRegionListener(val owner: WorksheetFoldGroup) extends FoldingListener {
    override def onFoldRegionStateChange(region: FoldRegion): Unit = 
      if (region.isExpanded) owner.onExpand(region)
      else owner.onCollapse(region)

    override def onFoldProcessingEnd() {}
  }

  // TODO: refactor, logic completely unreadable
  private def extractMappings(parsedRegions: Seq[ParsedRegion], originalDocument: Document, viewerDocument: Document): Seq[(Int, Int)] = {
    val result = ArrayBuffer[(Int, Int)]()

    if (parsedRegions.length > 1) {
      val fakeEndRegion = {
        val viewerLength   = viewerDocument.getTextLength - 1
        val originalLength = originalDocument.getTextLength - 1
        ParsedRegion(viewerLength , viewerLength , expanded = false, originalLength, 0, 1)
      }

      val regionsFinal = parsedRegions.tail :+ fakeEndRegion
      regionsFinal.foldLeft(parsedRegions.head) { case (previous, current) =>
        val previousLeftStart = originalDocument.getLineNumber(previous.leftStart - 1)
        result += ((previousLeftStart, viewerDocument.getLineNumber(previous.start)))

        if (previous.leftSideLength + previousLeftStart - 1 < originalDocument.getLineNumber(current.leftStart - 1)) {
          val rightSideBase = viewerDocument.getLineNumber(previous.end)

          for (j <- 1 to (originalDocument.getLineNumber(current.leftStart - 1) - previous.leftSideLength - previousLeftStart + 1)) {
            result += ((previousLeftStart + j, rightSideBase + j))
          }
        }

        current
      }
    } 

    result
  }

  def save(file: ScalaFile, group: WorksheetFoldGroup): Unit = {
    val virtualFile = file.getVirtualFile
    if (!virtualFile.isValid) return

    val regionsSerialized = serializeFoldRegions(group.regions)
    FileAttributeUtilCache.writeAttribute(WORKSHEET_PERSISTENT_FOLD_KEY, file, regionsSerialized)
  }

  def load(viewerEditor: Editor, originalEditor: Editor, project: Project,
           splitter: WorksheetDiffSplitters.SimpleWorksheetSplitter,
           file: PsiFile): WorksheetFoldGroup = {
    val group = new WorksheetFoldGroup(viewerEditor, originalEditor, project, splitter)

    val parsedRegions = extractRegions(file)
    parsedRegions.foreach(group.addParsedRegions)

    group
  }
  
  def computeMappings(viewerEditor: Editor, originalEditor: Editor, file: PsiFile): Seq[(Int, Int)] = {
    val parsedRegionsOpt = extractRegions(file)
    parsedRegionsOpt.fold(Seq((0, 0))) { parsed =>
      val mappings = extractMappings(parsed, originalEditor.getDocument, viewerEditor.getDocument)
      mappings.headOption match {
        case Some((_, 0)) => mappings
        case _ => (0, 0) +: mappings
      }
    }
  }


  private def extractRegions(file: PsiFile): Option[Seq[ParsedRegion]] = {
    val regionsSerialized = FileAttributeUtilCache.readAttribute(WORKSHEET_PERSISTENT_FOLD_KEY, file).filter(_.nonEmpty)
    regionsSerialized.map(deserializeFoldRegions)
  }

  private object FoldRegionSerializer {

    private val FieldSeparator = ','
    private val RegionsSeparator = '|'

    case class ParsedRegion(start: Int, end: Int, expanded: Boolean, leftStart: Int, spaces: Int, leftSideLength: Int)

    def serializeFoldRegions(regions: Seq[FoldRegionInfo]): String = {
      val regionsSerialized = regions.map {
        case FoldRegionInfo(region, expanded, trueStart, spaces, lsLength) =>
          val fields = Seq(region.getStartOffset, region.getEndOffset, expanded, trueStart, spaces, lsLength)
          fields.mkString(FieldSeparator.toString)
      }
      regionsSerialized.mkString(RegionsSeparator.toString)
    }

    def deserializeFoldRegions(text: String): Seq[ParsedRegion] = {
      val regionsDumps  = text.split(RegionsSeparator)
      val regionsFields = regionsDumps.map(_.split(FieldSeparator))
      regionsFields.collect {
        case Array(start, end, expanded, leftStart, spaces, leftSideLength) =>
          ParsedRegion(start.toInt, end.toInt, expanded == "true", leftStart.toInt, spaces.toInt, leftSideLength.toInt)
      }
    }
  }
}
