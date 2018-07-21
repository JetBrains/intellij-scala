package org.jetbrains.plugins.scala.lang.formatting.processors

import java.awt.Point

import com.intellij.application.options.CodeStyle
import com.intellij.lang.ASTNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.{Balloon, JBPopupFactory}
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.{Key, TextRange}
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.psi._
import com.intellij.psi.impl.source.codeStyle.PreFormatProcessor
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.awt.RelativePoint
import org.jetbrains.plugins.scala.lang.formatting.processors.ScalaFmtConfigUtil._
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockStatement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScBlockImpl
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.project.UserDataHolderExt
import org.scalafmt.Formatted.Success
import org.scalafmt.Scalafmt
import org.jetbrains.plugins.scala.extensions._

import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

class ScalaFmtPreFormatProcessor extends PreFormatProcessor {
  override def process(element: ASTNode, range: TextRange): TextRange = {
    val psiFile = Option(element.getPsi).map(_.getContainingFile)
    if (!psiFile.exists(CodeStyle.getCustomSettings(_, classOf[ScalaCodeStyleSettings]).USE_SCALAFMT_FORMATTER)) return range
    psiFile match {
      case Some(null) | None => TextRange.EMPTY_RANGE
      case _ if range.isEmpty => TextRange.EMPTY_RANGE
      case Some(file: ScalaFile) =>
        ScalaFmtPreFormatProcessor.formatIfRequired(file, ScalaFmtPreFormatProcessor.shiftRange(file, range))
        TextRange.EMPTY_RANGE
      case _ => range
    }
  }

  override def changesWhitespacesOnly(): Boolean = true
}
object ScalaFmtPreFormatProcessor {

  private def shiftRange(file: PsiFile, range: TextRange): TextRange = {
    rangesDeltaCache.get(file).map{ deltas =>
      var startOffset = range.getStartOffset
      val iterator = deltas.iterator
      if (deltas.isEmpty) return range
      var currentDelta = (0, 0)
      while (iterator.hasNext && currentDelta._1 < startOffset) {
        currentDelta = iterator.next()
        if (currentDelta._1 <= startOffset) startOffset += currentDelta._2
      }
      new TextRange(startOffset, startOffset + range.getLength)
    }.getOrElse(range)
  }

  private def formatIfRequired(file: PsiFile, range: TextRange): Unit = {
    val cached = file.getOrUpdateUserData(FORMATTED_RANGES_KEY, (new TextRanges, file.getModificationStamp))

    if (cached._2 == file.getModificationStamp && cached._1.contains(range)) return

    //formatter implicitly supposes that ranges starting on psi element start also reformat ws before the element
    val startElement = file.findElementAt(range.getStartOffset)
    val rangeUpdated = if (startElement != null && startElement.getTextRange.getStartOffset == range.getStartOffset &&
      !startElement.isInstanceOf[PsiWhiteSpace]) {
      val prev = PsiTreeUtil.prevLeaf(startElement, true)
      if (prev != null && !prev.isInstanceOf[PsiWhiteSpace]) new TextRange(prev.getTextRange.getEndOffset - 1, range.getEndOffset) else range
    } else range

    formatRange(file, rangeUpdated).foreach { delta =>
      def moveRanges(textRanges: TextRanges): TextRanges = {
        textRanges.ranges.map { otherRange =>
          if (otherRange.getEndOffset <= rangeUpdated.getStartOffset) otherRange
          else if (otherRange.getStartOffset >= rangeUpdated.getEndOffset) otherRange.shiftRight(delta)
          else TextRange.EMPTY_RANGE
        }.foldLeft(new TextRanges())((acc, aRange) => acc.union(aRange))
      }

      val ranges = if (cached._2 == file.getModificationStamp) moveRanges(cached._1) else new TextRanges

      if (rangeUpdated.getLength + delta > 0) file.putUserData(FORMATTED_RANGES_KEY, (ranges.union(rangeUpdated.grown(delta)), file.getModificationStamp))
    }
  }

  private def formatRange(file: PsiFile, range: TextRange): Option[Int] = {
    val project = file.getProject
    val manager = PsiDocumentManager.getInstance(project)
    val document = manager.getDocument(file)
    if (document == null) return None
    implicit val fileText: String = file.getText
    val config = configFor(file)
    if (range == file.getTextRange) {
      Scalafmt.format(fileText, config) match {
        case Success(formattedCode) =>
          inWriteAction(document.setText(formattedCode))
          manager.commitAllDocuments()
          return None
        case _ =>
      }
    }
    val configFiltered = disableRewriteRules(config)
    val (elements, formatted, wrapped) =
      elementsInRangeWrapped(file, range) match {
        case wrapElements if wrapElements.isEmpty && range != file.getTextRange =>
          //failed to wrap some elements, try the whole file
          Scalafmt.format(fileText, configFiltered) match {
            case Success(formattedCode) =>
              (Seq(file), formattedCode, false)
            case _ =>
              reportInvalidCodeFailure(project)
              return None
          }
        case wrapElements if wrapElements.isEmpty =>
          //wanted to format whole file, failed with file and with file elements wrapped, report failure
          reportInvalidCodeFailure(project)
          return None
        case wrapElements =>
          val elementsText = wrapElements.map(getText).mkString("")
          Scalafmt.format(wrap(elementsText), configFiltered) match {
            case Success(formattedCode) => (wrapElements, formattedCode, true)
            case _ =>
              reportInvalidCodeFailure(project)
              return None
          }
      }
    val wrapFile = PsiFileFactory.getInstance(project).createFileFromText("ScalaFmtFormatWrapper", ScalaFileType.INSTANCE, formatted)
    val textRangeDelta = replaceWithFormatted(wrapFile, elements, range, wrapped)
    manager.commitDocument(document)
    Some(textRangeDelta)
  }

  private val wrapPrefix = "class ScalaFmtFormatWrapper {\n"
  private val wrapSuffix = "\n}"

  //Use this since calls to 'getText' for inner elements of big files are somewhat expensive
  private def getText(element: PsiElement)(implicit fileText: String) =
    fileText.substring(element.getTextRange.getStartOffset, element.getTextRange.getEndOffset)

  private def wrap(elementText: String): String =
    wrapPrefix + elementText + wrapSuffix

  private def unwrap(wrapFile: PsiFile): Seq[PsiElement] = {
    val templateBody = PsiTreeUtil.findChildOfType(wrapFile, classOf[ScTemplateBody])
    if (templateBody == null) Seq.empty
    else {
      //get rid of braces and whitespaces near them
      templateBody.children.toList
        .drop(2).dropRight(2)
    }
  }

  private def isWhitespace(element: PsiElement) = element.isInstanceOf[PsiWhiteSpace]

  private def isProperUpperLevelPsi(element: PsiElement): Boolean = element match {
    case block: ScBlockImpl => block.getFirstChild.getNode.getElementType == ScalaTokenTypes.tLBRACE &&
      block.getLastChild.getNode.getElementType == ScalaTokenTypes.tRBRACE
    case _: ScBlockStatement | _: ScMember | _: PsiWhiteSpace => true
    case l: LeafPsiElement => l.getElementType == ScalaTokenTypes.tIDENTIFIER
    case _ => false
  }

  @tailrec
  private def elementsInRangeWrapped(file: PsiFile, range: TextRange, selectChildren: Boolean = true)(implicit fileText: String): Seq[PsiElement] = {
    val startElement = file.findElementAt(range.getStartOffset)
    val endElement = file.findElementAt(range.getEndOffset - 1)
    if (startElement == null || endElement == null) return Seq.empty
    def findProperParent(parent: PsiElement): Seq[PsiElement] = {
      val proper = ScalaPsiUtil.getParentWithProperty(parent, strict = false, isProperUpperLevelPsi)
      proper match {
        case Some(properParent) => Seq(properParent)
        case _ => Seq.empty
      }
    }
    val res: Seq[PsiElement] = Option(PsiTreeUtil.findCommonParent(startElement, endElement)) match {
      case Some(parent: LeafPsiElement) => findProperParent(parent)
      case Some(parent) =>
        val rawChildren = parent.children.toArray
        var children = rawChildren.filter(_.getTextRange.intersects(range))

        //drop unnecessary whitespaces
        while (children.headOption.exists(isWhitespace)) children = children.tail
        while (children.lastOption.exists(isWhitespace)) children = children.dropRight(1)

        if (children.isEmpty) Seq.empty
        else if (selectChildren && children.forall(isProperUpperLevelPsi)) {
          //for uniformity use the upper-most of embedded elements with same contents
          if (children.length == rawChildren.length && isProperUpperLevelPsi(parent)) Seq(parent)
          else children
        }
        else if (isProperUpperLevelPsi(parent)) Seq(parent)
        else findProperParent(parent)
      case _ => Seq.empty
    }
    if (res.length == 1 && res.head.isInstanceOf[PsiWhiteSpace]) {
      val ws = res.head
      val next = PsiTreeUtil.nextLeaf(ws)
      if (next == null) return Seq(ws) //don't touch the last WS, nobody should try to format it anyway
      val prev = PsiTreeUtil.prevLeaf(ws)
      val newRange =
        if (prev == null) range.union(next.getTextRange)
        else prev.getTextRange.union(next.getTextRange)
      elementsInRangeWrapped(file, newRange, selectChildren = false)
    } else res
  }

  @tailrec
  private def getElementIndent(element: PsiElement)(implicit fileText: String): Int = {
    val prevLeaf = PsiTreeUtil.prevLeaf(element, true)
    if (prevLeaf == null) return 0
    element match {
      case ws: PsiWhiteSpace =>
        val wsText = ws.getText
        if (wsText.contains("\n"))
          wsText.substring(wsText.lastIndexOf("\n") + 1).length
        else getElementIndent(prevLeaf)
      case _ => getElementIndent(prevLeaf)
    }
  }

  private def replaceWithFormatted(wrapFile: PsiFile, elements: Seq[PsiElement], range: TextRange, isWrapped: Boolean)(implicit fileText: String): Int = {
    val replaceElements: Seq[PsiElement] = if (isWrapped) unwrap(wrapFile) else Seq(wrapFile)
    val project = elements.head.getProject
    val elementsToTraverse: ListBuffer[(PsiElement, PsiElement)] = ListBuffer(replaceElements zip elements:_*).sortBy(_._2.getTextRange.getStartOffset)
    val additionalIndent = if (isWrapped) getElementIndent(elements.head) - 2 else 0
    val changes = buildChangesList(elementsToTraverse, range, additionalIndent, project)
    applyChanges(changes, range)
  }

  private sealed trait PsiChange {
    private var myIsValid = true
    def isValid: Boolean = myIsValid
    final def applyAndGetDelta(): Int = {
      myIsValid = false
      doApply()
    }
    def doApply(): Int
    def isInRange(range: TextRange): Boolean
    def getStartOffset: Int
  }

  private case class Replace(original: PsiElement, formatted: PsiElement) extends PsiChange {
    def doApply(): Int = {
      val commonPrefixLength = StringUtil.commonPrefix(original.getText, formatted.getText).length
      val delta = addDelta(original.getTextRange.getStartOffset + commonPrefixLength, original.getContainingFile, formatted.getTextLength - original.getTextLength)
      inWriteAction(original.replace(formatted))
      delta
    }
    override def isInRange(range: TextRange): Boolean = original.getTextRange.intersectsStrict(range)
    override def getStartOffset: Int = original.getTextRange.getStartOffset
  }

  private case class Insert(before: PsiElement, formatted: PsiElement) extends PsiChange {
    override def doApply(): Int = {
      val parent = before.getParent
      if (parent != null) {
        inWriteAction(parent.addBefore(formatted, before))
      }
      addDelta(formatted, formatted.getTextLength)
    }
    override def isInRange(range: TextRange): Boolean = range.contains(before.getTextRange.getStartOffset)
    override def getStartOffset: Int = before.getTextRange.getStartOffset
  }

  private case class Remove(remove: PsiElement) extends PsiChange {
    override def doApply(): Int = {
      val res = addDelta(remove, -remove.getTextLength)
      inWriteAction(remove.delete())
      res
    }
    override def isInRange(range: TextRange): Boolean = remove.getTextRange.intersectsStrict(range)
    override def getStartOffset: Int = remove.getTextRange.getStartOffset
  }

  private def buildChangesList(elementsToTraverse: ListBuffer[(PsiElement, PsiElement)], range: TextRange, additionalIndent: Int, project: Project): ListBuffer[PsiChange] = {
    val res = ListBuffer[PsiChange]()
    def widenWs(ws: PsiElement): PsiElement = {
      if (additionalIndent == 0) ws
      else {
        val wsText = ws.getText
        if (!wsText.contains("\n")) ws
        else if (additionalIndent >= 0) ScalaPsiElementFactory.createWhitespace(wsText + (" " * additionalIndent))(project)
        else {
          val newlineIndex = wsText.lastIndexOf("\n") + 1
          val trailingWhitespaces = wsText.substring(newlineIndex).length
          ScalaPsiElementFactory.createWhitespace(wsText.substring(0, newlineIndex + Math.max(0, trailingWhitespaces + additionalIndent)))(project)
        }
      }
    }
    def traverseSettingWs(formatted: PsiElement, original: PsiElement): Unit = {
      var formattedIndex = 0
      var originalIndex = 0
      if (isWhitespace(formatted) && isWhitespace(original)) {
        res += Replace(original, widenWs(formatted))
      }

      val formattedChildren = formatted.children.toArray
      val originalChildren = original.children.toArray

      while (formattedIndex < formattedChildren.length && originalIndex < originalChildren.length) {
        val originalElement = originalChildren(originalIndex)
        val formattedElement = formattedChildren(formattedIndex)
        val isInRange = originalElement.getTextRange.intersectsStrict(range)
        (originalElement, formattedElement) match {
          case (originalWs: PsiWhiteSpace, formattedWs: PsiWhiteSpace) => //replace whitespace
            if (originalWs.getText != formattedWs.getText) res += Replace(originalWs, widenWs(formattedWs))
            originalIndex += 1
            formattedIndex += 1
          case (_, formattedWs: PsiWhiteSpace) => //a whitespace has been added
            res += Insert(originalElement, widenWs(formattedWs))
            formattedIndex += 1
          case (originalWs: PsiWhiteSpace, _) => //a whitespace has been removed
            res += Remove(originalWs)
            originalIndex += 1
          case _ =>
            if (isInRange) {
              elementsToTraverse += ((formattedElement, originalElement))
            }
            originalIndex += 1
            formattedIndex += 1
        }
      }
    }
    while(elementsToTraverse.nonEmpty) {
      val head = elementsToTraverse.head
      elementsToTraverse.remove(0)
      traverseSettingWs(head._1, head._2)
    }
    res
  }

  private def applyChanges(changes: ListBuffer[PsiChange], range: TextRange): Int = {
    changes.filter(_.isInRange(range)).filter(_.isValid).sortBy(_.getStartOffset).foldLeft(0){
      case (delta, change) => delta + change.applyAndGetDelta()
    }
  }

  def reportError(errorText: String, project: Project): Unit = {
    val popupFactory = JBPopupFactory.getInstance
    val frame = WindowManagerEx.getInstanceEx.getFrame(project)
    if (frame == null) return
    val balloon = popupFactory.createHtmlTextBalloonBuilder(
      errorText,
      MessageType.WARNING,
      null).createBalloon
    balloon.show(new RelativePoint(frame, new Point(frame.getWidth - 20, 20)), Balloon.Position.above)
  }

  private def reportInvalidCodeFailure(project: Project): Unit = {
    if (ScalaCodeStyleSettings.getInstance(project).SHOW_SCALAFMT_INVALID_CODE_WARNINGS)
      reportError("Failed to find correct surrounding code to pass for scalafmt, no formatting will be performed", project)
  }

  //TODO get rid of this once com.intellij.util.text.TextRanges does not have an error on unifying (x, x+1) V (x+1, y)
  class TextRanges(val ranges: Seq[TextRange]) {
    def this() = this(Seq.empty)

    def contains(range: TextRange): Boolean = ranges.exists(_.contains(range))

    def union(range: TextRange): TextRanges = if (contains(range)) this else {
      val intersections = ranges.filter(_.intersects(range))
      val newRanges = if (intersections.isEmpty) {
        ranges :+ range
      } else {
        ranges.filter(!_.intersects(range)) :+ intersections.foldLeft(range)((acc, nextRange) => acc.union(nextRange))
      }
      new TextRanges(newRanges)
    }
  }

  private val FORMATTED_RANGES_KEY: Key[(TextRanges, Long)] = Key.create("scala.fmt.formatted.ranges")

  private val rangesDeltaCache: mutable.Map[PsiFile, mutable.TreeSet[(Int, Int)]] = mutable.Map[PsiFile, mutable.TreeSet[(Int, Int)]]()

  private def addDelta(offset: Int, containingFile: PsiFile, delta: Int): Int = {
    val cache = rangesDeltaCache.getOrElseUpdate(containingFile, mutable.TreeSet[(Int, Int)]())
    cache.add(offset, delta)
    delta
  }

  private def addDelta(element: PsiElement, delta: Int): Int = addDelta(element.getTextRange.getStartOffset, element.getContainingFile, delta)

  def clearRangesCache(): Unit = rangesDeltaCache.clear()
}