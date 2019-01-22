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
import com.intellij.psi.impl.source.codeStyle.{CodeEditUtil, PreFormatProcessor}
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.awt.RelativePoint
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiUtil, TypeAdjuster}
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaRecursiveElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockStatement, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScBlockImpl
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.project.UserDataHolderExt
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.processors.ScalaFmtPreFormatProcessor.{formatIfRequired, shiftRange}
import org.scalafmt.Formatted.Success
import org.scalafmt.Scalafmt
import org.scalafmt.config.{RewriteSettings, ScalafmtConfig}

import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

class ScalaFmtPreFormatProcessor extends PreFormatProcessor {
  override def process(element: ASTNode, range: TextRange): TextRange = {
    val psiFile = Option(element.getPsi).flatMap(_.getContainingFile.toOption)

    val useScalaFmt = psiFile.exists(CodeStyle.getCustomSettings(_, classOf[ScalaCodeStyleSettings]).USE_SCALAFMT_FORMATTER)
    if (!useScalaFmt) return range

    psiFile match {
      case None => TextRange.EMPTY_RANGE
      case _ if range.isEmpty => TextRange.EMPTY_RANGE
      case Some(file: ScalaFile) =>
        val isSubrangeFormatting = range != file.getTextRange
        if (isSubrangeFormatting && getScalaSettings(file).USE_INTELLIJ_FORMATTER_FOR_SCALAFMT_RANGE_FORMAT) {
          range
        } else {
          formatIfRequired(file, shiftRange(file, range))
          TextRange.EMPTY_RANGE
        }
      case _ => range
    }
  }

  override def changesWhitespacesOnly(): Boolean = false

  private def getScalaSettings(el: PsiElement ): ScalaCodeStyleSettings = ScalaCodeStyleSettings.getInstance(el.getProject)
}

object ScalaFmtPreFormatProcessor {

  private def shiftRange(file: PsiFile, range: TextRange): TextRange = {
    rangesDeltaCache.get(file).filterNot(_.isEmpty).map { deltas =>
      var startOffset = range.getStartOffset
      val iterator = deltas.iterator
      var currentDelta = (0, 0)
      while (iterator.hasNext && currentDelta._1 < startOffset) {
        currentDelta = iterator.next()
        if (currentDelta._1 <= startOffset)
          startOffset += currentDelta._2
      }
      new TextRange(startOffset, startOffset + range.getLength)
    }.getOrElse(range)
  }

  private def formatIfRequired(file: PsiFile, range: TextRange): Unit = {
    val (cachedRange, timeStamp) = file.getOrUpdateUserData(FORMATTED_RANGES_KEY, (new TextRanges, file.getModificationStamp))

    if (timeStamp == file.getModificationStamp && cachedRange.contains(range))
      return

    val rangeUpdated = fixRangeStartingOnPsiElement(file, range)

    formatRange(file, rangeUpdated).foreach { delta: Int =>
      def moveRanges(textRanges: TextRanges): TextRanges = {
        textRanges.ranges.map { otherRange =>
          if (otherRange.getEndOffset <= rangeUpdated.getStartOffset) otherRange
          else if (otherRange.getStartOffset >= rangeUpdated.getEndOffset) otherRange.shiftRight(delta)
          else TextRange.EMPTY_RANGE
        }.foldLeft(new TextRanges)((acc, aRange) => acc.union(aRange))
      }

      val ranges =
        if (timeStamp == file.getModificationStamp) moveRanges(cachedRange)
        else new TextRanges

      if (rangeUpdated.getLength + delta > 0)
        file.putUserData(FORMATTED_RANGES_KEY, (ranges.union(rangeUpdated.grown(delta)), file.getModificationStamp))
    }
  }

  // formatter implicitly supposes that ranges starting on psi element start also reformat ws before the element
  private def fixRangeStartingOnPsiElement(file: PsiFile, range: TextRange): TextRange = {
    val startElement = file.findElementAt(range.getStartOffset)

    val doesRangeStartOnPsiElement =
      startElement != null && !startElement.isInstanceOf[PsiWhiteSpace] &&
        startElement.getTextRange.getStartOffset == range.getStartOffset

    if (doesRangeStartOnPsiElement) {
      val prev = PsiTreeUtil.prevLeaf(startElement, true)
      if (prev == null || prev.isInstanceOf[PsiWhiteSpace]) range
      else new TextRange(prev.getTextRange.getEndOffset - 1, range.getEndOffset)
    } else {
      range
    }
  }

  private def formatInSingleFile(elements: Seq[PsiElement], config: ScalafmtConfig, project: Project, shouldWrap: Boolean): Option[String] = {
    val elementsText = elements.foldLeft("") { case (acc, element) => acc + element.getText }
    val wrapped = if (shouldWrap) wrap(elementsText) else elementsText
    Scalafmt.format(wrapped, config).toEither.toOption
  }

  private def attachFormattedCode(elements: Seq[PsiElement], config: ScalafmtConfig, project: Project): Seq[(PsiElement, String)] = {
    val nonWsElements = elements.filterNot(_.isInstanceOf[PsiWhiteSpace])
    nonWsElements.map(_.getText).map(wrap).map(Scalafmt.format(_, config)) match {
      case successful => (nonWsElements zip successful).collect {
        case (element, Success(formattedCode)) => (element, formattedCode)
      }
      case failure => // FIXME: unreachable code
        reportInvalidCodeFailure(project)
        Seq.empty
    }
  }

  private def formatRange(file: PsiFile, range: TextRange): Option[Int] = {
    val project = file.getProject
    val manager = PsiDocumentManager.getInstance(project)
    val document = manager.getDocument(file)
    if (document == null) return None
    implicit val fileText: String = file.getText
    val config = ScalaFmtConfigUtil.configFor(file)

    def formatWholeFile(): Boolean = {
      Scalafmt.format(fileText, config) match {
        case Success(formattedCode) =>
          inWriteAction(document.setText(formattedCode))
          manager.commitAllDocuments() // do we really need to commit all documents instead of just single?
          true
        case _ =>
          false
      }
    }

    if (range == file.getTextRange && formatWholeFile())
      return None

    def processRange(elements: Seq[PsiElement], wrap: Boolean): Option[Int] = {
      val hasRewriteRules = config.rewrite.rules.nonEmpty
      val rewriteElements: Seq[PsiElement] = if (hasRewriteRules) elements.flatMap(maybeRewriteElements(_, range)) else Seq.empty
      val rewriteElementsToFormatted: Seq[(PsiElement, String)] = attachFormattedCode(rewriteElements, config, project)
      val noRewriteConfig = if (hasRewriteRules) config.copy(rewrite = RewriteSettings()) else config
      val formattedText: String = formatInSingleFile(elements, noRewriteConfig, project, wrap) match {
        case Some(formatted) => formatted
        case None => return None
      }

      val textRangeDelta = replaceWithFormatted(elements, formattedText, rewriteElementsToFormatted, range, wrap)
      manager.commitDocument(document)
      Some(textRangeDelta)
    }

    val elementsWrapped: Seq[PsiElement] = elementsInRangeWrapped(file, range)
    if (elementsWrapped.isEmpty) {
      if (range != file.getTextRange) {
        //failed to wrap some elements, try the whole file
        processRange(Seq(file), wrap = false)
      } else {
        //wanted to format whole file, failed with file and with file elements wrapped, report failure
        reportInvalidCodeFailure(project)
      }
      None
    } else {
      processRange(elementsWrapped, wrap = true)
    }
  }

  private val wrapPrefix = "class ScalaFmtFormatWrapper {\n"
  private val wrapSuffix = "\n}"

  //Use this since calls to 'getText' for inner elements of big files are somewhat expensive
  private def getText(element: PsiElement)(implicit fileText: String): String =
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

  private def isWhitespace(element: PsiElement): Boolean = element.isInstanceOf[PsiWhiteSpace]

  private def isProperUpperLevelPsi(element: PsiElement): Boolean = element match {
    case block: ScBlockImpl => block.getFirstChild.getNode.getElementType == ScalaTokenTypes.tLBRACE &&
      block.getLastChild.getNode.getElementType == ScalaTokenTypes.tRBRACE
    case _: ScBlockStatement | _: ScMember | _: PsiWhiteSpace | _: PsiComment => true
    case l: LeafPsiElement => l.getElementType == ScalaTokenTypes.tIDENTIFIER
    case _ => false
  }

  private def elementsInRangeWrapped(file: PsiFile, range: TextRange, selectChildren: Boolean = true)(implicit fileText: String): Seq[PsiElement] = {
    val startElement = file.findElementAt(range.getStartOffset)
    val endElement = file.findElementAt(range.getEndOffset - 1)
    if (startElement == null || endElement == null)
      return Seq.empty

    def findProperParent(parent: PsiElement): Seq[PsiElement] = {
      ScalaPsiUtil.getParentWithProperty(parent, strict = false, isProperUpperLevelPsi).toList
    }

    val res: Seq[PsiElement] = Option(PsiTreeUtil.findCommonParent(startElement, endElement)) match {
      case Some(parent: LeafPsiElement) => findProperParent(parent)
      case Some(parent) =>
        val rawChildren = parent.children.toArray
        val children = rawChildren.filter(_.getTextRange.intersects(range))
          .dropWhile(isWhitespace).reverse //drop unnecessary whitespaces
          .dropWhile(isWhitespace).reverse

        if (children.isEmpty) Seq.empty
        else if (selectChildren && children.forall(isProperUpperLevelPsi) && !parent.isInstanceOf[ScExpression]) {
          //for uniformity use the upper-most of embedded elements with same contents
          if (children.length == rawChildren.length && isProperUpperLevelPsi(parent)) Seq(parent)
          else children
        }
        else if (isProperUpperLevelPsi(parent)) Seq(parent)
        else findProperParent(parent)
      case _ => Seq.empty
    }

    res.toList match {
      case (head: PsiWhiteSpace) :: Nil =>
        surroundWhitespaceWithContext(head, range, file)
      case _ =>
        res
    }
  }

  // if selection contains only whitespaces we should search for some informative psi elements to the left and right
  private def surroundWhitespaceWithContext(ws: PsiWhiteSpace, range: TextRange, file: PsiFile)(implicit fileText: String): Seq[PsiElement] = {
    val next = PsiTreeUtil.nextLeaf(ws, true)
    if (next == null) return Seq(ws) //don't touch the last WS, nobody should try to format it anyway
    val prev = PsiTreeUtil.prevLeaf(ws, true)
    val newRange =
      if (prev == null) range.union(next.getTextRange)
      else prev.getTextRange.union(next.getTextRange)
    elementsInRangeWrapped(file, newRange, selectChildren = false)
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

  private def maybeRewriteElements(element: PsiElement, range: TextRange, withRewrite: List[PsiElement] = List()): List[PsiElement] = {
    if (!range.intersects(element.getTextRange)) {
      withRewrite
    } else if (range.contains(element.getTextRange) && isProperUpperLevelPsi(element)) {
      element :: withRewrite
    } else {
      val children = element.children.toList
      children.foldLeft(withRewrite) { case (acc, child) => maybeRewriteElements(child, range, acc) }
    }
  }

  private def unwrapPsiFromFormattedFile(elements: Seq[PsiElement], formattedText: String,
                                         project: Project, shouldUnwrap: Boolean): Seq[(PsiElement, PsiElement)] = {
    val wrapFile = PsiFileFactory.getInstance(project).createFileFromText("ScalaFmtFormatWrapper", ScalaFileType.INSTANCE, formattedText)
    elements zip (if (shouldUnwrap) unwrap(wrapFile) else Seq(wrapFile))
  }

  private def unwrapPsiFromFormattedElements(elementsToFormatted: Seq[(PsiElement, String)], project: Project): Seq[(PsiElement, Seq[PsiElement])] =
    elementsToFormatted.map { case (element, formattedText) =>
      val unwrapped = unwrap(PsiFileFactory.getInstance(project).createFileFromText("ScalaFmtFormatWrapper", ScalaFileType.INSTANCE, formattedText))
      (element, unwrapped)
    }.sortBy(_._1.getTextRange.getStartOffset)

  private def replaceWithFormatted(elements: Seq[PsiElement],
                                   formattedText: String,
                                   rewriteToFormatted: Seq[(PsiElement, String)],
                                   range: TextRange, shouldUnwrap: Boolean)(implicit fileText: String): Int = {
    val project = elements.head.getProject
    val elementsToTraverse: ListBuffer[(PsiElement, PsiElement)] = ListBuffer(unwrapPsiFromFormattedFile(elements, formattedText, project, shouldUnwrap): _*)
    val rewriteElementsToTraverse: Seq[(PsiElement, Seq[PsiElement])] = unwrapPsiFromFormattedElements(rewriteToFormatted, project)
    val additionalIndent = getElementIndent(elements.head) - 2
    val changes = buildChangesList(elementsToTraverse, rewriteElementsToTraverse, range, additionalIndent, project, fileText)
    applyChanges(changes, range)
  }

  private class AdjustIndentsVisitor(val additionalIndent: Int, val project: Project) extends ScalaRecursiveElementVisitor {
    override def visitWhiteSpace(space: PsiWhiteSpace): Unit = {
      val replacer = getIndentAdjustedWhitespace(space, additionalIndent, project)
      if (replacer != space) inWriteAction(space.replace(replacer))
    }
  }

  private def getIndentAdjustedWhitespace(ws: PsiWhiteSpace, additionalIndent: Int, project: Project): PsiElement = {
    val text = ws.getText
    if (!text.contains("\n")) return ws
    if (additionalIndent == 0) ws
    else if (additionalIndent > 0) ScalaPsiElementFactory.createWhitespace(text + (" " * additionalIndent))(project)
    else {
      val newlineIndex = text.lastIndexOf("\n") + 1
      val trailingWhitespaces = text.substring(newlineIndex).length
      ScalaPsiElementFactory.createWhitespace(text.substring(0, newlineIndex + Math.max(0, trailingWhitespaces + additionalIndent)))(project)
    }
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

  private val generatedVisitor: PsiRecursiveElementVisitor = new PsiRecursiveElementVisitor() {
    override def visitElement(element: PsiElement): Unit = {
      CodeEditUtil.setNodeGenerated(element.getNode, false)
      super.visitElement(element)
    }
  }

  private def setNotGenerated(text: String, offset: Int, parent: PsiElement): Unit = {
    val children = parent.children.find(child => child.getTextRange.getStartOffset == offset && child.getText == text)
    children.foreach { child =>
      child.accept(generatedVisitor)
      //sometimes when an element is inserted, its neighbours also get the 'isGenerated' flag set, fix this
      Option(child.getPrevSibling).foreach(_.accept(generatedVisitor))
      Option(child.getNextSibling).foreach(_.accept(generatedVisitor))
    }
  }

  private case class Replace(original: PsiElement, formatted: PsiElement) extends PsiChange {
    override def toString: String = s"${original.getTextRange}: ${original.getText} -> ${formatted.getText}"
    override def doApply(): Int = {
      if (!formatted.isValid || !original.isValid) {
        return 0
      }
      val commonPrefixLength = StringUtil.commonPrefix(original.getText, formatted.getText).length
      val delta = addDelta(original.getTextRange.getStartOffset + commonPrefixLength, original.getContainingFile, formatted.getTextLength - original.getTextLength)
      val parent = original.getParent
      val offset = original.getTextOffset
      formatted.accept(generatedVisitor)
      inWriteAction(original.replace(formatted))
      setNotGenerated(formatted.getText, offset, parent)
      delta
    }
    override def isInRange(range: TextRange): Boolean = original.getTextRange.intersectsStrict(range)
    override def getStartOffset: Int = original.getTextRange.getStartOffset
  }

  private case class Insert(before: PsiElement, formatted: PsiElement) extends PsiChange {
    override def doApply(): Int = {
      if (!formatted.isValid) {
        return 0
      }

      val originalMarkedForAdjustment = TypeAdjuster.isMarkedForAdjustment(before)
      val parent = before.getParent
      val offset = before.getTextRange.getStartOffset
      formatted.accept(generatedVisitor)

      val inserted =
        if (parent != null) {
          Option(inWriteAction(parent.addBefore(formatted, before)))
        } else None

      setNotGenerated(formatted.getText, offset, parent)

      if (originalMarkedForAdjustment)
        inserted.foreach(TypeAdjuster.markToAdjust)

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

  private def processElementToElementReplace(original: PsiElement, formatted: PsiElement,
                                             rewriteElementsToTraverse: Seq[(PsiElement, Seq[PsiElement])],
                                             range: TextRange, project: Project, additionalIndent: Int,
                                             elementsToTraverse: ListBuffer[(PsiElement, PsiElement)],
                                             res: ListBuffer[PsiChange], originalText: String): Unit = {
    if (isWhitespace(formatted) && isWhitespace(original)) {
      res += Replace(original, getIndentAdjustedWhitespace(formatted.asInstanceOf[PsiWhiteSpace], additionalIndent, project))
      return
    }

    val formattedElementText = formatted.getText
    val originalElementText = getText(original)(originalText)
    val rewriteElement = rewriteElementsToTraverse.find(original == _._1)

    if (formattedElementText == originalElementText && rewriteElement.isEmpty) return

    if (range.contains(original.getTextRange)) {
      rewriteElement match {
        case Some((_, replacement)) =>
          res ++= replace(original, replacement, additionalIndent)
        case _ =>
          formatted.accept(new AdjustIndentsVisitor(additionalIndent, project))
          res += Replace(original, formatted)
      }
    } else if (isSameElementType(original, formatted)) {
      var formattedIndex = 0
      var originalIndex = 0

      val formattedChildren = formatted.children.toArray
      val originalChildren = original.children.toArray

      if (originalChildren.isEmpty) return

      while (formattedIndex < formattedChildren.length && originalIndex < originalChildren.length) {
        val originalElement = originalChildren(originalIndex)
        val formattedElement = formattedChildren(formattedIndex)
        val isInRange = originalElement.getTextRange.intersectsStrict(range)
        (originalElement, formattedElement) match {
          case (originalWs: PsiWhiteSpace, formattedWs: PsiWhiteSpace) => //replace whitespace
            if (originalWs.getText != formattedWs.getText) res += Replace(originalWs, getIndentAdjustedWhitespace(formattedWs, additionalIndent, project))
            originalIndex += 1
            formattedIndex += 1
          case (_, formattedWs: PsiWhiteSpace) => //a whitespace has been added
            res += Insert(originalElement, getIndentAdjustedWhitespace(formattedWs, additionalIndent, project))
            formattedIndex += 1
          case (originalWs: PsiWhiteSpace, _) => //a whitespace has been removed
            res += Remove(originalWs)
            originalIndex += 1
          case _ =>
            if (isInRange) {
              elementsToTraverse += ((originalElement, formattedElement))
            }
            originalIndex += 1
            formattedIndex += 1
        }
      }
    }
  }

  private def replace(element: PsiElement, formattedElements: Seq[PsiElement], additionalIndent: Int): Seq[PsiChange] = {
    if (formattedElements.length == 1 && formattedElements.head.getText == element.getText) return Seq.empty
    formattedElements.foreach(_.accept(new AdjustIndentsVisitor(additionalIndent, element.getProject)))
    formattedElements.map(Insert(element, _)) :+ Remove(element)
  }

  private def buildChangesList(elementsToTraverse: ListBuffer[(PsiElement, PsiElement)],
                               rewriteElementsToTraverse: Seq[(PsiElement, Seq[PsiElement])],
                               range: TextRange, additionalIndent: Int,
                               project: Project, originalText: String): ListBuffer[PsiChange] = {
    val res = ListBuffer[PsiChange]()
    while (elementsToTraverse.nonEmpty) {
      val head = elementsToTraverse.head
      elementsToTraverse.remove(0)
      processElementToElementReplace(head._1, head._2, rewriteElementsToTraverse, range, project, additionalIndent,
        elementsToTraverse, res, originalText)
    }
    res
  }

  private def isSameElementType(original: PsiElement, formatted: PsiElement): Boolean = {
    val originalNode = original.getNode
    val formattedNode = formatted.getNode
    originalNode != null && formattedNode != null && originalNode.getElementType == formattedNode.getElementType
  }

  private def applyChanges(changes: ListBuffer[PsiChange], range: TextRange): Int = {
    changes.filter(_.isInRange(range)).filter(_.isValid).sorted(Ordering.fromLessThan[PsiChange] {
      case (_: Insert, _) => true
      case (_, _: Insert) => false
      case (_: Replace, _: Remove) => true
      case (_: Remove, _: Replace) => false
      case (left: PsiChange, right: PsiChange) => left.getStartOffset < right.getStartOffset
    }).foldLeft(0) {
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

  private val rangesDeltaCache: mutable.Map[PsiFile, mutable.TreeSet[(Int, Int)]] = mutable.WeakHashMap[PsiFile, mutable.TreeSet[(Int, Int)]]()

  private def addDelta(offset: Int, containingFile: PsiFile, delta: Int): Int = {
    val cache = rangesDeltaCache.getOrElseUpdate(containingFile, mutable.TreeSet[(Int, Int)]())
    cache.add(offset, delta)
    delta
  }

  private def addDelta(element: PsiElement, delta: Int): Int = addDelta(element.getTextRange.getStartOffset, element.getContainingFile, delta)

  def clearRangesCache(): Unit = {
    rangesDeltaCache.clear()
  }
}