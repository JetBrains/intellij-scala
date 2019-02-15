package org.jetbrains.plugins.scala.lang.formatting.processors

import com.intellij.application.options.CodeStyle
import com.intellij.lang.ASTNode
import com.intellij.notification._
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.{Key, TextRange}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi._
import com.intellij.psi.impl.source.codeStyle.{CodeEditUtil, PreFormatProcessor}
import com.intellij.psi.impl.source.tree.{LeafPsiElement, PsiWhiteSpaceImpl}
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.util.PsiTreeUtil
import org.apache.commons.lang.StringUtils
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, _}
import org.jetbrains.plugins.scala.lang.formatting.processors.ScalaFmtPreFormatProcessor.{formatIfRequired, shiftRange}
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.{ScalafmtDynamicConfig, ScalafmtReflect}
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.{ScalafmtDynamicConfigUtil, ScalafmtNotifications}
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScBlockStatement, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScBlockImpl
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiUtil, TypeAdjuster}
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment
import org.jetbrains.plugins.scala.project.UserDataHolderExt

import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.{ArrayBuffer, ListBuffer}
import scala.util.Try
import scala.util.control.NonFatal
import scala.xml.Utility

class ScalaFmtPreFormatProcessor extends PreFormatProcessor {
  private val log = Logger.getInstance(getClass)

  override def process(element: ASTNode, range: TextRange): TextRange = {
    val psiFile = Option(element.getPsi).flatMap(_.getContainingFile.toOption)

    val useScalaFmt = psiFile.exists(CodeStyle.getCustomSettings(_, classOf[ScalaCodeStyleSettings]).USE_SCALAFMT_FORMATTER)
    if (!useScalaFmt) return range

    psiFile match {
      case None => TextRange.EMPTY_RANGE
      case _ if range.isEmpty => TextRange.EMPTY_RANGE
      case Some(file: ScalaFile) =>
        val isSubrangeFormatting = range != file.getTextRange
        if (isSubrangeFormatting && getScalaSettings(file).SCALAFMT_USE_INTELLIJ_FORMATTER_FOR_RANGE_FORMAT) {
          range
        } else {
          try formatIfRequired(file, shiftRange(file, range)) catch {
            case NonFatal(ex) =>
              log.error("An error occurred during scalafmt formatting", ex)
              throw ex
          }
          TextRange.EMPTY_RANGE
        }
      case _ => range
    }
  }

  override def changesWhitespacesOnly(): Boolean = false

  private def getScalaSettings(el: PsiElement): ScalaCodeStyleSettings = ScalaCodeStyleSettings.getInstance(el.getProject)
}

object ScalaFmtPreFormatProcessor {
  private val StartMarker = "/**StartMarker*/"
  private val EndMarker = "/**EndMarker*/"

  private val ScalaFmtIndent: Int = 2

  private val DummyWrapperClassName = "ScalaFmtFormatWrapper"
  private val DummyWrapperClassPrefix = s"class $DummyWrapperClassName {\n"
  private val DummyWrapperClassSuffix = "\n}"

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

    val rangeStartsOnPsiElement =
      startElement != null && !startElement.isInstanceOf[PsiWhiteSpace] &&
        startElement.getTextRange.getStartOffset == range.getStartOffset

    if (rangeStartsOnPsiElement) {
      val prev = PsiTreeUtil.prevLeaf(startElement, true)
      if (prev == null || (prev.isInstanceOf[PsiWhiteSpace] && !prev.getText.contains("\n"))) {
        range
      } else {
        new TextRange(prev.getTextRange.getEndOffset - 1, range.getEndOffset)
      }
    } else {
      range
    }
  }

  private def formatInSingleFile(elements: Seq[PsiElement], config: ScalafmtDynamicConfig, shouldWrap: Boolean)
                                (implicit project: Project): Option[WrappedCode] = {
    val wrappedCode: WrappedCode =
      if (shouldWrap) {
        wrap(elements)
      } else {
        val elementsText = elements.foldLeft("") { case (acc, element) => acc + element.getText }
        new WrappedCode(elementsText, wrapped = false, wrappedInHelperClass = false)
      }

    val scalaFmt: ScalafmtReflect = config.fmtReflect
    scalaFmt.format(wrappedCode.text, config).toOption.map { formattedText =>
      wrappedCode.withText(formattedText)
    }
  }

  /** The method tries to wrap psi elements with all it's parents until root is reached, removing
    * all unrelated sibling elements.
    *
    * @example suppose elements are `val x = 2` and `val y = 42` in following code:
    * {{{
    *  class A { ... }
    *  class B {
    *    def foo: Int = ???
    *    def bar: Unit = {
    *      object X {
    *        val x = 2
    *        val y = 42
    *      }
    *    }
    *    private val mur = ???
    *  }
    *  class C { ... }
    * }}}
    * the resulting wrapped code text will be:
    * {{{
    *  class B {
    *    def bar: Unit = {
    *      object X {
    *        val x = 2
    *        val y = 42
    *      }
    *    }
    *  }
    * }}}
    */
  private def wrap(elements: Seq[PsiElement])(implicit project: Project): WrappedCode = {
    require(elements.nonEmpty, "expected elements to be non empty")

    val firstElement = elements.head
    val lastElement = elements.last

    assert(elements.forall(_.getParent == firstElement.getParent), "elements should have the same parent")

    val rootNodeCopy: PsiFile = findPsiFileRoot(firstElement).copy().asInstanceOf[PsiFile]

    val firstElementInCopy = findElementAtRange(rootNodeCopy, firstElement.getTextRange)
    val lastElementInCopy = findElementAtRange(rootNodeCopy, lastElement.getTextRange)

    val elementsInCopy = this.getElementsOfRange(firstElementInCopy, lastElementInCopy).toArray

    import ScalaPsiElementFactory.{createDocComment, createNewLine}
    val startMarkers: Seq[PsiElement] = firstElementInCopy.prependSiblings(createDocComment(StartMarker), createNewLine())
    val endMarkers: Seq[PsiElement] = lastElementInCopy.appendSiblings(createNewLine(), createDocComment(EndMarker))

    def canContainRemovableChildren(element: PsiElement): Boolean = element match {
      case _: ScBlock | _: ScTemplateBody | _: ScalaFile | _: ScPackaging | _: ScPatternDefinition => true
      case _ => false
    }

    def canRemoveChild(child: PsiElement, prevElements: Seq[PsiElement]): Boolean = child match {
      case _: PsiWhiteSpace => false
      case leaf: LeafPsiElement if isCurlyBrace(leaf) => false
      case _ => !prevElements.contains(child)
    }

    @tailrec
    def doWrap(element: PsiElement, prevElements: Seq[PsiElement]): WrappedCode = {
      if (canContainRemovableChildren(element)) {
        // we have to materialize children to array because delete operation affects behaviour used in ChildrenIterator
        val children = element.children.toArray
        for {
          child <- children if canRemoveChild(child, prevElements)
        } element.deleteChildRange(child, child)
      }

      element.parent match {
        case Some(parent) =>
          doWrap(parent, prevElements = Seq(element))
        case _ =>
          val text = element.getText
          if (areAllUpperElementTypeDefinitions(prevElements)) {
            new WrappedCode(text, wrapped = true, wrappedInHelperClass = false)
          } else {
            // scalafmt can only deal with files that contain class/trait/object definitions
            // so in case of scala worksheets we need to wrap the code with a helper class
            val wrappedInHelper = wrapInHelperClass(text)
            new WrappedCode(wrappedInHelper, wrapped = true, wrappedInHelperClass = true)
          }
      }
    }

    doWrap(firstElementInCopy.getParent, startMarkers ++ elementsInCopy ++ endMarkers)
  }

  @tailrec
  private def areAllUpperElementTypeDefinitions(elements: Seq[PsiElement]): Boolean = {
    elements.headOption match {
      case Some(p: ScPackaging) =>
        val children = p.getChildren.drop(1) // drop package reference
        areAllUpperElementTypeDefinitions(children)
      case _ =>
        val elementsToConsider = elements.filter {
          case _: ScImportStmt | _: ScDocComment | _: PsiComment | _: PsiDocComment | _: PsiWhiteSpace => false
          case _ => true
        }
        elementsToConsider.forall(_.isInstanceOf[ScTypeDefinition])
    }
  }

  @inline
  private def isCurlyBrace(node: ASTNode): Boolean = {
    node.getElementType == ScalaTokenTypes.tLBRACE ||
      node.getElementType == ScalaTokenTypes.tRBRACE
  }

  private def findElementAtRange(root: PsiFile, range: TextRange): PsiElement = {
    val elementAtRange = PsiTreeUtil.findElementOfClassAtRange(root, range.getStartOffset, range.getEndOffset, classOf[PsiElement])
    elementAtRange match {
      // we do not want to select whole file in case range starts at the beginning of the file
      case file: PsiFile => file.getFirstChild
      case res => res
    }
  }

  private def findPsiFileRoot(element: PsiElement): PsiFile = {
    element.parentOfType(classOf[PsiFile]).getOrElse {
      throw new RuntimeException(s"Could not find root of type 'PsiFile' for element ${element.getNode.getElementType}")
    }
  }

  private def attachFormattedCode(elements: Seq[PsiElement], config: ScalafmtDynamicConfig)(implicit project: Project): Seq[(PsiElement, WrappedCode)] = {
    val elementsWithoutWs = elements.filterNot(_.isInstanceOf[PsiWhiteSpace])
    val elementsFormatted = elementsWithoutWs.map(el => (el, formatInSingleFile(Seq(el), config, shouldWrap = true)))
    elementsFormatted.collect { case (el, Some(code)) => (el, code) }
  }

  def formatWithoutCommit(file: PsiFile, respectProjectMatcher: Boolean): Unit = {
    val config = ScalafmtDynamicConfigUtil.configOptForFile(file).orNull
    if (config == null || respectProjectMatcher && !ScalafmtDynamicConfigUtil.isIncludedInProject(file, config))
      return

    formatWithoutCommit(file, config) match {
      case Left(error: ScalafmtFormatError) =>
        reportInvalidCodeFailure(file.getProject, file, Some(error))
      case _ =>
    }
  }

  private def formatWithoutCommit(file: PsiFile, config: ScalafmtDynamicConfig): Either[FormattingError, Unit] = {
    val scalaFmt: ScalafmtReflect = config.fmtReflect
    for {
      document <- Option(PsiDocumentManager.getInstance(file.getProject).getDocument(file)).toRight(DocumentNotFoundError)
      formattedText <- Try(scalaFmt.format(file.getText, config)).toEither.left.map(ScalafmtFormatError)
    } yield {
      inWriteAction(document.setText(formattedText))
    }
  }


  private def formatRange(file: PsiFile, range: TextRange): Option[Int] = {
    implicit val project: Project = file.getProject
    val manager = PsiDocumentManager.getInstance(project)
    val document = manager.getDocument(file)
    if (document == null) return None
    implicit val fileText: String = file.getText

    val config: ScalafmtDynamicConfig = ScalafmtDynamicConfigUtil.configOptForFile(file).orNull
    if (config == null) return None

    val rangeIncludesWholeFile = range.contains(file.getTextRange)

    var wholeFileFormatError: Option[ScalafmtFormatError] = None
    if (rangeIncludesWholeFile) {
      formatWithoutCommit(file, config) match {
        case Right(_) =>
          manager.commitDocument(document)
          return None
        case Left(error: ScalafmtFormatError) =>
          wholeFileFormatError = Some(error)
        case _ =>
      }
    }

    def processRange(elements: Seq[PsiElement], wrap: Boolean): Option[Int] = {
      val hasRewriteRules = config.hasRewriteRules
      val rewriteElements: Seq[PsiElement] = if (hasRewriteRules) elements.flatMap(maybeRewriteElements(_, range)) else Seq.empty
      val rewriteElementsToFormatted: Seq[(PsiElement, WrappedCode)] = attachFormattedCode(rewriteElements, config)
      val noRewriteConfig = if (hasRewriteRules) config.withoutRewriteRules else config

      val result = formatInSingleFile(elements, noRewriteConfig, wrap).map { formatted =>
        val textRangeDelta = replaceWithFormatted(elements, formatted, rewriteElementsToFormatted, range)
        manager.commitDocument(document)
        textRangeDelta
      }
      if (result.isEmpty) {
        reportInvalidCodeFailure(project, file, wholeFileFormatError)
      }
      result
    }

    val elementsWrapped: Seq[PsiElement] = elementsInRangeWrapped(file, range)
    if (elementsWrapped.isEmpty) {
      if (rangeIncludesWholeFile) {
        //wanted to format whole file, failed with file and with file elements wrapped, report failure
        reportInvalidCodeFailure(project, file, wholeFileFormatError)
      } else {
        //failed to wrap some elements, try the whole file
        processRange(Seq(file), wrap = false)
      }
      None
    } else {
      processRange(elementsWrapped, wrap = true)
    }
  }

  //Use this since calls to 'getText' for inner elements of big files are somewhat expensive
  private def getText(element: PsiElement)(implicit fileText: String): String =
    getText(element.getTextRange)(fileText)

  private def getText(range: TextRange)(implicit fileText: String): String =
    fileText.substring(range.getStartOffset, range.getEndOffset)

  private def unwrap(wrapFile: PsiFile)(implicit project: Project): Seq[PsiElement] = {
    val text = wrapFile.getText

    // we need to call extra `getParent` because findElementAt returns DOC_COMMENT_START
    val startMarker = wrapFile.findElementAt(text.indexOf(StartMarker)).getParent
    val endMarker = wrapFile.findElementAt(text.indexOf(EndMarker)).getParent

    assert(startMarker.isInstanceOf[ScDocComment])
    assert(endMarker.isInstanceOf[ScDocComment])

    val startMarkerParent = startMarker.getParent

    // if docComment is followed by ScMember then is becomes its child, not previous sibling
    // we need to fix this here to properly get elements range later
    val isCommentStuckToElement = startMarkerParent.isInstanceOf[ScMember] && startMarkerParent.getFirstChild == startMarker
    val startMarkerFixed = if (!isCommentStuckToElement) startMarker else {
      val sibling = startMarker.getNextSibling
      startMarkerParent.deleteChildRange(startMarker, startMarker)
      sibling match {
        case ws: PsiWhiteSpace =>
          // delete new line after doc comment as well
          ws.getWhitespaceAfterFirstNewLine match {
            case Some(wsNew) => ws.replace(wsNew)
            case None => ws.delete()
          }
        case _ =>
      }
      startMarkerParent
    }

    var result = this.getElementsOfRange(startMarkerFixed, endMarker)
    if (result.headOption.contains(startMarker))
      result = result.drop(1)
    if (result.lastOption.contains(endMarker))
      result = result.dropRight(1)
    result
  }

  /** this is a partial copy of [[PsiTreeUtil.getElementsOfRange]] except this method does not fail
    * if sibling element becomes null, it simply stops iterating
    */
  private def getElementsOfRange(start: PsiElement, end: PsiElement): Seq[PsiElement] = {
    val result = new ArrayBuffer[PsiElement]
    var e: PsiElement = start
    while (e.ne(end) && e != null) {
      result += e
      e = e.getNextSibling
    }
    result += end
    result
  }

  private def wrapInHelperClass(elementText: String): String =
    DummyWrapperClassPrefix + elementText + DummyWrapperClassSuffix

  private def trimWhitespacesOrEmpty(elements: Seq[PsiElement]): Seq[PsiElement] = {
    elements
      .dropWhile(isWhitespaceOrEmpty).reverse
      .dropWhile(isWhitespaceOrEmpty).reverse
  }

  private def isWhitespaceOrEmpty(element: PsiElement): Boolean = {
    element.isInstanceOf[PsiWhiteSpace] || element.getTextLength == 0
  }

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

    val commonParent = Option(PsiTreeUtil.findCommonParent(startElement, endElement))
    val res: Seq[PsiElement] = commonParent match {
      case Some(parent: LeafPsiElement) =>
        findProperParent(parent)
      case Some(parent) =>
        val childrenAll = parent.children.toArray
        val childrenFitRange = childrenAll.filter(_.getTextRange.intersects(range))
        val children = trimWhitespacesOrEmpty(childrenFitRange)

        if (children.isEmpty) {
          Seq.empty
        } else if (selectChildren && children.forall(isProperUpperLevelPsi) && !parent.isInstanceOf[ScExpression]) {
          //for uniformity use the upper-most of embedded elements with same contents
          if (children.length == childrenAll.length && isProperUpperLevelPsi(parent)) {
            Seq(parent)
          } else {
            children
          }
        } else if (isProperUpperLevelPsi(parent)) {
          Seq(parent)
        } else {
          findProperParent(parent)
        }
      case _ =>
        Seq.empty
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

  private def getElementIndent(element: PsiElement): Int =
    prevSiblingAsWhitespace(element).map(_.getIndentSize).getOrElse(0)

  private def prevSiblingAsWhitespace(element: PsiElement): Option[PsiWhiteSpace] =
    Option(element.getPrevSibling).filterByType[PsiWhiteSpace]

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

  private def unwrapPsiFromFormattedFile(formattedCode: WrappedCode)(implicit project: Project): Seq[PsiElement] = {
    val wrapFile = PsiFileFactory.getInstance(project).createFileFromText(DummyWrapperClassName, ScalaFileType.INSTANCE, formattedCode.text)
    val elementsUnwrapped =
      if (formattedCode.wrapped) unwrap(wrapFile)
      else Seq(wrapFile)
    trimWhitespacesOrEmpty(elementsUnwrapped)
  }

  private def unwrapPsiFromFormattedElements(elementsToFormatted: Seq[(PsiElement, WrappedCode)])
                                            (implicit project: Project): Seq[(PsiElement, Seq[PsiElement])] =
    elementsToFormatted.map { case (element, formattedCode) =>
      val unwrapped = unwrapPsiFromFormattedFile(formattedCode)
      (element, unwrapped)
    }.sortBy(_._1.getTextRange.getStartOffset)

  private def replaceWithFormatted(elements: Seq[PsiElement],
                                   formattedCode: WrappedCode,
                                   rewriteToFormatted: Seq[(PsiElement, WrappedCode)],
                                   range: TextRange)
                                  (implicit project: Project, fileText: String): Int = {
    val elementsUnwrapped: Seq[PsiElement] = unwrapPsiFromFormattedFile(formattedCode)
    val elementsToTraverse: Seq[(PsiElement, PsiElement)] = elements.zip(elementsUnwrapped)
    val rewriteElementsToTraverse: Seq[(PsiElement, Seq[PsiElement])] = unwrapPsiFromFormattedElements(rewriteToFormatted)

    val additionalIndent = if (formattedCode.wrappedInHelperClass) -ScalaFmtIndent else 0

    val changes = buildChangesList(elementsToTraverse, rewriteElementsToTraverse, range, additionalIndent)
    applyChanges(changes, range)
  }

  private class AdjustIndentsVisitor(val additionalIndent: Int, val project: Project) extends PsiRecursiveElementVisitor {
    override def visitElement(element: PsiElement): Unit = {
      ProgressIndicatorProvider.checkCanceled()
      element.children.foreach(_.accept(this))
    }

    override def visitWhiteSpace(space: PsiWhiteSpace): Unit = {
      val replacer = space.withAdditionalIndent(additionalIndent)(project)
      if (replacer != space) inWriteAction(space.replace(replacer))
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
      val delta = addDelta(
        original.getTextRange.getStartOffset + commonPrefixLength,
        original.getContainingFile,
        formatted.getTextLength - original.getTextLength
      )
      val parent = original.getParent
      val offset = original.getTextOffset
      formatted.accept(generatedVisitor)

      if (formatted == EmptyPsiWhitespace) {
        inWriteAction(original.delete())
      } else {
        inWriteAction(original.replace(formatted))
      }

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
                                             range: TextRange, additionalIndent: Int,
                                             elementsToTraverse: ListBuffer[(PsiElement, PsiElement)],
                                             res: ListBuffer[PsiChange])(implicit project: Project, originalText: String): Unit = {
    (original, formatted) match {
      case (ws1: PsiWhiteSpace, ws2: PsiWhiteSpace) =>
        res += Replace(ws1, ws2.withAdditionalIndent(additionalIndent))
        return
      case _ =>
    }

    val formattedElementText = formatted.getText
    val originalElementText = getText(original)
    val rewriteElement = rewriteElementsToTraverse.find(original == _._1)

    val originalIndent = getElementIndent(original)
    val formattedIndent = getElementIndent(formatted)

    val nothingChanged =
      formattedElementText == originalElementText &&
        formattedIndent == originalIndent &&
        rewriteElement.isEmpty

    if (nothingChanged)
      return

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
            if (originalWs.getText != formattedWs.getText) res += Replace(originalWs, formattedWs.withAdditionalIndent(additionalIndent))
            originalIndex += 1
            formattedIndex += 1
          case (_, formattedWs: PsiWhiteSpace) => //a whitespace has been added
            res += Insert(originalElement, formattedWs.withAdditionalIndent(additionalIndent))
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
    val isOneToOneReplacement = formattedElements.length == 1
    if (isOneToOneReplacement && formattedElements.head.getText == element.getText) {
      Seq.empty
    } else {
      formattedElements.foreach(_.accept(new AdjustIndentsVisitor(additionalIndent, element.getProject)))
      if (isOneToOneReplacement) {
        Seq(Replace(element, formattedElements.head))
      } else {
        formattedElements.map(Insert(element, _)) :+ Remove(element)
      }
    }
  }

  private def buildChangesList(elementsToTraverse: Seq[(PsiElement, PsiElement)],
                               rewriteElementsToTraverse: Seq[(PsiElement, Seq[PsiElement])],
                               range: TextRange, additionalIndent: Int)
                              (implicit project: Project, originalText: String): Seq[PsiChange] = {
    val changes = ListBuffer[PsiChange]()
    fixFirstElementIndent(elementsToTraverse, additionalIndent, changes)

    val elementsBuffer = ListBuffer(elementsToTraverse: _*)
    while (elementsBuffer.nonEmpty) {
      val (original, formatted) = elementsBuffer.remove(0)
      processElementToElementReplace(
        original, formatted, rewriteElementsToTraverse, range, additionalIndent, elementsBuffer, changes
      )
    }

    changes
  }

  private def fixFirstElementIndent(elementsToTraverse: Seq[(PsiElement, PsiElement)],
                                    additionalIndent: Int, changes: ListBuffer[PsiChange])
                                   (implicit project: Project): Unit = {
    for {
      (original, formatted) <- elementsToTraverse.headOption
      prevWsOriginal <- prevSiblingAsWhitespace(original)
      prevWsFormatted <- prevSiblingAsWhitespace(formatted)
    } {
      val indentFormatted = prevWsFormatted.getIndentSize
      val prevWsOriginalFixed = prevWsOriginal
        .withIndent(indentFormatted + additionalIndent)
        .getOrElse(EmptyPsiWhitespace)
      changes += Replace(prevWsOriginal, prevWsOriginalFixed)
    }
  }

  private def isSameElementType(original: PsiElement, formatted: PsiElement): Boolean = {
    val originalNode = original.getNode
    val formattedNode = formatted.getNode
    originalNode != null && formattedNode != null && originalNode.getElementType == formattedNode.getElementType
  }

  private def applyChanges(changes: Seq[PsiChange], range: TextRange): Int = {
    // changes order: Inserts, Replaces, Removes
    val changesFinal = changes.filter(_.isInRange(range)).filter(_.isValid).sorted(Ordering.fromLessThan[PsiChange] {
      case (_: Insert, _) => true
      case (_, _: Insert) => false
      case (_: Replace, _: Remove) => true
      case (_: Remove, _: Replace) => false
      case (left: PsiChange, right: PsiChange) => left.getStartOffset < right.getStartOffset
    })
    changesFinal.foldLeft(0) { case (delta, change) =>
      delta + change.applyAndGetDelta()
    }
  }

  private def reportError(message: String, actions: Seq[NotificationAction]): Unit = {
    ScalafmtNotifications.displayNotification(message, NotificationType.ERROR, actions)
  }

  private def reportInvalidCodeFailure(project: Project, file: PsiFile, error: Option[ScalafmtFormatError] = None): Unit = {
    if (ScalaCodeStyleSettings.getInstance(project).SCALAFMT_SHOW_INVALID_CODE_WARNINGS) {
      val (message, action) = error.map(_.cause) match {
        case Some(cause: scala.meta.ParseException) =>
          val action = file.getVirtualFile.toOption.map(new OpenFileNotificationActon(project, _, cause.pos.start.offset))
          (s"Parse error: ${cause.getMessage}", action)
        case Some(cause) =>
          (cause.getMessage.take(100), None)
        case None =>
          ("Failed to find correct surrounding code to pass for scalafmt, no formatting will be performed", None)
      }
      reportError(Utility.escape(message), action.toSeq)
    }
  }

  class OpenFileNotificationActon(project: Project, vFile: VirtualFile, offset: Int, title: String = "open source file")
    extends NotificationAction(title) {

    override def actionPerformed(e: AnActionEvent, notification: Notification): Unit = {
      new OpenFileDescriptor(project, vFile, offset).navigate(true)
      notification.expire()
    }
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

  private def addDelta(element: PsiElement, delta: Int): Int =
    addDelta(element.getTextRange.getStartOffset, element.getContainingFile, delta)

  def clearRangesCache(): Unit = {
    rangesDeltaCache.clear()
  }

  private implicit class PsiWhiteSpaceExt(val ws: PsiWhiteSpace) extends AnyVal {

    /** @return Some(whitespace) with `indent` spaces after last new line character or ws beginning <br>
      *         None if resulting whitespace text is empty, due to PsiWhitespace can't contain empty text
      * @example (" \n \n ", 3) -> Some(" \n \n   ")
      * @example ("", 3)      -> Some("   ")
      * @example ("", 0)      -> None
      */
    def withIndent(indent: Int)(implicit project: Project): Option[PsiElement] = {
      require(indent >= 0, "expecting non negative indent")

      val text = ws.getText
      val newLineIndex = text.lastIndexOf('\n') + 1
      val beforeNewLine = StringUtils.substring(text, 0, newLineIndex)
      val textNew = beforeNewLine + " " * indent
      if (textNew.isEmpty) {
        None
      } else {
        Some(ScalaPsiElementFactory.createWhitespace(textNew))
      }
    }

    def withAdditionalIndent(additionalIndent: Int)(implicit project: Project): PsiElement = {
      val text = ws.getText
      if (!text.contains("\n") || additionalIndent == 0) {
        ws
      } else if (additionalIndent > 0) {
        ScalaPsiElementFactory.createWhitespace(text + (" " * additionalIndent))
      } else {
        val newLineIndex = text.lastIndexOf("\n") + 1
        val currentIndent = text.length - newLineIndex
        val finalIndent = Math.max(0, currentIndent + additionalIndent)
        ScalaPsiElementFactory.createWhitespace(text.substring(0, newLineIndex + finalIndent))
      }
    }

    /** @example {{{"\t " -> "\t "}}}
      * @example {{{" \n \n\t " -> "\t "}}}
      */
    def getWhitespaceAfterFirstNewLine(implicit project: Project): Option[PsiElement] = {
      val text = ws.getText
      val index = text.indexOf('\n')
      val rest = StringUtils.substring(text, index + 1)
      if (rest.isEmpty) {
        None
      } else {
        Some(ScalaPsiElementFactory.createWhitespace(rest))
      }
    }

    def getIndentSize: Int = {
      val text = ws.getText
      val newLineIndex = text.lastIndexOf("\n") + 1
      text.length - newLineIndex
    }
  }

  private sealed trait FormattingError
  private object DocumentNotFoundError extends FormattingError
  private case class ScalafmtFormatError(cause: Throwable) extends FormattingError

  /** This is a helper class to keep information about how formatted elements were wrapped
    */
  private class WrappedCode(val text: String, val wrapped: Boolean, val wrappedInHelperClass: Boolean) {
    def withText(newText: String): WrappedCode = new WrappedCode(newText, wrapped, wrappedInHelperClass)
  }

  /** Marker whitespace implementation that indicates absence of whitespace.
    * It is more convenient to use Replace psi change instead of Remove, but there is no way
    * to create a whitespace with empty text normally, via ScalaPsiElementFactory, so we use this hack
    */
  private object EmptyPsiWhitespace extends PsiWhiteSpaceImpl("") {
    override def isValid: Boolean = true
  }
}