package org.jetbrains.plugins.scala.lang.formatting.scalafmt.processors

import com.intellij.application.options.CodeStyle
import com.intellij.lang.ASTNode
import com.intellij.notification._
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.{Key, TextRange}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi._
import com.intellij.psi.impl.source.codeStyle.PreFormatProcessor
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.util.PsiTreeUtil
import javax.swing.event.HyperlinkEvent
import org.apache.commons.lang.StringUtils
import org.jetbrains.annotations.{NonNls, TestOnly}
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, _}
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.exceptions.{PositionExceptionImpl, ReflectionException}
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.{ScalafmtReflect, ScalafmtReflectConfig}
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.processors.PsiChange._
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.processors.ScalaFmtPreFormatProcessor._
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.{ScalafmtDynamicConfigService, ScalafmtNotifications}
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolatedStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScConstructorPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScParameterizedTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScBlockStatement, ScExpression, ScInfixExpr}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScBlockImpl
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment
import org.jetbrains.plugins.scala.project.UserDataHolderExt
import org.jetbrains.plugins.scala.{ScalaBundle, ScalaFileType}

import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.{ArrayBuffer, ListBuffer}
import scala.util.Try
import scala.util.control.NonFatal

class ScalaFmtPreFormatProcessor extends PreFormatProcessor {

  override def process(element: ASTNode, range: TextRange): TextRange = {
    val psiFile = containingFile(element) match {
      case file: ScalaFile => file
      case null            => return TextRange.EMPTY_RANGE // we could probably return original range here as well
      case _               => return range
    }

    if (range.isEmpty)
      return TextRange.EMPTY_RANGE

    val useScalafmt = CodeStyle.getCustomSettings(psiFile, classOf[ScalaCodeStyleSettings]).USE_SCALAFMT_FORMATTER
    if (!useScalafmt)
      return range

    val isSubrangeFormatting = range != psiFile.getTextRange
    val skip = isSubrangeFormatting && getScalaSettings(psiFile).SCALAFMT_USE_INTELLIJ_FORMATTER_FOR_RANGE_FORMAT
    if (skip)
      range
    else {
      try formatIfRequired(psiFile, shiftRange(psiFile, range)) catch {
        case NonFatal(ex) =>
          reportUnknownError(ex)
          throw ex
      }
      TextRange.EMPTY_RANGE
    }
  }

  private def containingFile(element: ASTNode): PsiFile = {
    val psi = element.getPsi
    if (psi == null) return null
    psi.getContainingFile
  }

  override def changesWhitespacesOnly(): Boolean = false

  private def getScalaSettings(el: PsiElement): ScalaCodeStyleSettings = ScalaCodeStyleSettings.getInstance(el.getProject)
}

//noinspection HardCodedStringLiteral
object ScalaFmtPreFormatProcessor {

  private val Log = Logger.getInstance(getClass)

  private val StartMarker = "/**StartMarker*/"
  private val EndMarker = "/**EndMarker*/"

  private val ScalaFmtIndent: Int = 2

  private val DummyWrapperClassName = "ScalaFmtFormatWrapper"
  private val DummyWrapperClassPrefix = s"class $DummyWrapperClassName {\n"
  private val DummyWrapperClassSuffix = "\n}"

  @TestOnly
  val formattedCountMap = new java.util.concurrent.ConcurrentHashMap[VirtualFile, Int]()

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
    val (cachedRange, cachedFileTimeStamp, cachedConfigTimestamp) =
      file.getOrUpdateUserData(FORMATTED_RANGES_KEY, (new TextRanges, file.getModificationStamp, None))

    val (config, configTimestamp) = ScalafmtDynamicConfigService.instanceIn(file).configForFileWithTimestamp(file) match {
      case Some(result) => result
      case None =>
        return
    }

    val nothingChanged = cachedFileTimeStamp == file.getModificationStamp &&
      cachedRange.contains(range) &&
      cachedConfigTimestamp == configTimestamp
    if (nothingChanged && !ApplicationManager.getApplication.isUnitTestMode)
      return

    val rangeUpdated = fixRangeStartingOnPsiElement(file, range)

    implicit val context: ConfigContext = ConfigContext(config, Option(file.getVirtualFile).safeMap(_.getCanonicalPath))

    val result = formatRange(file, rangeUpdated)
    for {
      res <- result
      _ = if (ApplicationManager.getApplication.isUnitTestMode) {
        formattedCountMap.merge(file.getVirtualFile, 1, (a, b) => a + b)
      }
      delta <- res
    } {
      def moveRanges(textRanges: TextRanges): TextRanges = {
        textRanges.ranges.map { otherRange =>
          if (otherRange.getEndOffset <= rangeUpdated.getStartOffset) otherRange
          else if (otherRange.getStartOffset >= rangeUpdated.getEndOffset) otherRange.shiftRight(delta)
          else TextRange.EMPTY_RANGE
        }.foldLeft(new TextRanges)((acc, aRange) => acc.union(aRange))
      }

      val ranges =
        if (cachedFileTimeStamp == file.getModificationStamp) moveRanges(cachedRange)
        else new TextRanges

      if (rangeUpdated.getLength + delta > 0) {
        val tuple = (ranges.union(rangeUpdated.grown(delta)), file.getModificationStamp, configTimestamp)
        file.putUserData(FORMATTED_RANGES_KEY, tuple)
      }
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
      if (prev != null && prev.isInstanceOf[PsiWhiteSpace]) {
        new TextRange(prev.getTextRange.getEndOffset - 1, range.getEndOffset)
      } else {
        range
      }
    } else {
      range
    }
  }

  private def formatInSingleFile(
    elements: Iterable[PsiElement],
    shouldWrap: Boolean
  )(implicit project: Project, context: ConfigContext): Option[WrappedCode] = {
    val wrappedCode: WrappedCode =
      if (shouldWrap) {
        wrap(elements)
      } else {
        val elementsText = elements.foldLeft("") { case (acc, element) => acc + element.getText }
        new WrappedCode(elementsText, wrapped = false, wrappedInHelperClass = false)
      }

    val scalaFmt: ScalafmtReflect = context.config.fmtReflect
    scalaFmt.tryFormat(wrappedCode.text) match {
      case Left(value) =>
        if (ApplicationManager.getApplication.isUnitTestMode)
          throw value.cause
        None
      case Right(formattedText) =>
        Some(wrappedCode.withText(formattedText))
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
  private def wrap(elements: Iterable[PsiElement])(implicit project: Project): WrappedCode = {
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

  private def attachFormattedCode(elements: collection.Seq[PsiElement])
                                 (implicit project: Project, context: ConfigContext): collection.Seq[(PsiElement, WrappedCode)] = {
    val elementsNonBlank = elements.filterNot(el => el.isInstanceOf[PsiWhiteSpace] || el.getTextLength == 0)
    val elementsFormatted = elementsNonBlank.map { el =>
      val formatted = formatInSingleFile(Seq(el), shouldWrap = true)
      (el, formatted)
    }
    elementsFormatted.collect { case (el, Some(code)) => (el, code) }
  }

  def formatWithoutCommit(file: PsiFile, document: Document, respectProjectMatcher: Boolean): Unit = {
    val configManager = ScalafmtDynamicConfigService.instanceIn(file.getProject)
    val config = configManager.configForFile(file).orNull
    if (config == null || respectProjectMatcher && !configManager.isFileIncludedInProject(file, config))
      return

    implicit val context: ConfigContext = ConfigContext(config, Option(file.getVirtualFile).safeMap(_.getCanonicalPath))
    formatWithoutCommit(document) match {
      case Left(error: ScalafmtFormatError) =>
        reportInvalidCodeFailure(file, Some(error))(file.getProject)
      case _ =>
    }
  }

  private def formatWithoutCommit(document: Document)
                                 (implicit context: ConfigContext): Either[FormattingError, Unit] = {
    val scalaFmt: ScalafmtReflect = context.config.fmtReflect
    for {
      formattedText <- scalaFmt.tryFormat(document.getText)
    } yield inWriteAction {
      document.setText(formattedText)
    }
  }

  private def formatRange(file: PsiFile, range: TextRange)
                         (implicit context: ConfigContext): Either[Unit, Option[Int]] = {
    implicit val project: Project = file.getProject
    val manager = PsiDocumentManager.getInstance(project)
    val document = manager.getDocument(file)
    if (document == null) return Left(())
    implicit val fileText: String = file.getText

    val rangeIncludesWholeFile = range.contains(file.getTextRange)
    var wholeFileFormatError: Option[ScalafmtFormatError] = None
    if (rangeIncludesWholeFile)
      formatWithoutCommit(document) match {
        case Right(_) =>
          manager.commitDocument(document)
          return Right(None)
        case Left(error: ScalafmtFormatError) =>
          wholeFileFormatError = Some(error)
        case _ =>
      }

    def processRange(elements: collection.Seq[PsiElement], wrap: Boolean): Either[Unit, Option[Int]] = {
      val hasRewriteRules = context.config.hasRewriteRules
      val rewriteElements: collection.Seq[PsiElement] = if (hasRewriteRules) elements.flatMap(maybeRewriteElements(_, range)) else Seq.empty
      val rewriteElementsToFormatted = attachFormattedCode(rewriteElements)
      val noRewriteConfig = if (hasRewriteRules) context.config.withoutRewriteRules else context.config

      val newContext = context.withConfig(noRewriteConfig)
      val result = formatInSingleFile(elements, wrap)(project, newContext).map { formatted =>
        val textRangeDelta = replaceWithFormatted(elements, formatted, rewriteElementsToFormatted, range)
        manager.commitDocument(document)
        textRangeDelta
      }

      if (result.isEmpty) {
        reportInvalidCodeFailure(file, wholeFileFormatError)
        Left(())
      } else {
        Right(result)
      }
    }

    val elementsWrapped: collection.Seq[PsiElement] = elementsInRangeWrapped(file, range)
    if (elementsWrapped.isEmpty) {
      if (rangeIncludesWholeFile) {
        //wanted to format whole file, failed with file and with file elements wrapped, report failure
        reportInvalidCodeFailure(file, wholeFileFormatError)
        Left(())
      } else {
        //failed to wrap some elements, try the whole file
        processRange(Seq(file), wrap = false)
        Right(None)
      }
    } else {
      processRange(elementsWrapped, wrap = true)
    }
  }

  //Use this since calls to 'getText' for inner elements of big files are somewhat expensive
  private def getText(element: PsiElement)(implicit fileText: String): String =
    getText(element.getTextRange)(fileText)

  private def getText(range: TextRange)(implicit fileText: String): String =
    fileText.substring(range.getStartOffset, range.getEndOffset)

  private def unwrap(wrapFile: PsiFile)(implicit project: Project): collection.Seq[PsiElement] = {
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
  private def getElementsOfRange(start: PsiElement, end: PsiElement): collection.Seq[PsiElement] = {
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

  private def trimWhitespacesOrEmpty(elements: collection.Seq[PsiElement]): collection.Seq[PsiElement] = {
    elements
      .dropWhile(isWhitespaceOrEmpty).reverse
      .dropWhile(isWhitespaceOrEmpty).reverse
  }

  private def isWhitespaceOrEmpty(element: PsiElement): Boolean = {
    element.isInstanceOf[PsiWhiteSpace] || element.getTextLength == 0
  }

  private def isProperUpperLevelPsi(element: PsiElement): Boolean = element match {
    case block: ScBlockImpl =>
      block.getFirstChild.elementType == ScalaTokenTypes.tLBRACE &&
        block.getLastChild.elementType == ScalaTokenTypes.tRBRACE
    case _: ScBlockStatement | _: ScMember | _: PsiWhiteSpace | _: PsiComment =>
      !element.getParent.isInstanceOf[ScInterpolatedStringLiteral]
    case l: LeafPsiElement =>
      l.getElementType == ScalaTokenTypes.tIDENTIFIER
    case _ =>
      false
  }

  private def elementsInRangeWrapped(file: PsiFile, range: TextRange, selectChildren: Boolean = true)
                                    (implicit fileText: String): collection.Seq[PsiElement] = {
    val startElement = file.findElementAt(range.getStartOffset)
    val endElement = file.findElementAt(range.getEndOffset - 1)
    if (startElement == null || endElement == null)
      return Seq.empty

    def findProperParent(parent: PsiElement): Seq[PsiElement] =
      ScalaPsiUtil.getParentWithProperty(parent, strict = false, isProperUpperLevelPsi).toList

    val commonParent =
      if (startElement == endElement) Option(startElement.getParent)
      else Option(PsiTreeUtil.findCommonParent(startElement, endElement))
    val res: collection.Seq[PsiElement] = commonParent match {
      case Some(parent: LeafPsiElement) =>
        findProperParent(parent)
      case Some(parent@Parent(_: ScConstructorPattern)) =>
        findProperParent(parent)
      case Some(parent@Parent(Parent(_: ScParameterizedTypeElement))) =>
        findProperParent(parent)
      case Some(parent) =>
        val childrenAll = parent.children.toArray
        val childrenFitRange = childrenAll.filter(_.getTextRange.intersects(range))
        val children = trimWhitespacesOrEmpty(childrenFitRange)
        val parentIsProper = isProperUpperLevelPsi(parent)

        if (children.isEmpty) {
          Seq.empty
        } else if (selectChildren && children.forall(isProperUpperLevelPsi) && !parent.isInstanceOf[ScExpression]) {
          //for uniformity use the upper-most of embedded elements with same contents
          if (children.length == childrenAll.length && parentIsProper) {
            Seq(parent)
          } else {
            children
          }
        } else if (parentIsProper) {
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
  private def surroundWhitespaceWithContext(ws: PsiWhiteSpace, range: TextRange, file: PsiFile)(implicit fileText: String)
  : collection.Seq[PsiElement] = {
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
    } else if (element.isInstanceOf[ScInfixExpr]) { // just run the tests...
      element :: withRewrite
    } else if (range.contains(element.getTextRange) && isProperUpperLevelPsi(element)) {
      element :: withRewrite
    } else {
      val children = element.children.toList
      children.foldLeft(withRewrite) { case (acc, child) => maybeRewriteElements(child, range, acc) }
    }
  }

  private def unwrapPsiFromFormattedFile(formattedCode: WrappedCode)(implicit project: Project): collection.Seq[PsiElement] = {
    val wrapFile = PsiFileFactory.getInstance(project).createFileFromText(DummyWrapperClassName, ScalaFileType.INSTANCE, formattedCode.text)
    val elementsUnwrapped =
      if (formattedCode.wrapped) unwrap(wrapFile)
      else Seq(wrapFile)
    trimWhitespacesOrEmpty(elementsUnwrapped)
  }

  private def unwrapPsiFromFormattedElements(elementsToFormatted: collection.Seq[(PsiElement, WrappedCode)])
                                            (implicit project: Project): collection.Seq[(PsiElement, collection.Seq[PsiElement])] =
    elementsToFormatted.map { case (element, formattedCode) =>
      val unwrapped = unwrapPsiFromFormattedFile(formattedCode)
      (element, unwrapped)
    }.sortBy(_._1.getTextRange.getStartOffset)

  private def replaceWithFormatted(elements: Iterable[PsiElement],
                                   formattedCode: WrappedCode,
                                   rewriteToFormatted: collection.Seq[(PsiElement, WrappedCode)],
                                   range: TextRange)
                                  (implicit project: Project, fileText: String): Int = {
    val elementsUnwrapped: collection.Seq[PsiElement] = unwrapPsiFromFormattedFile(formattedCode)
    val elementsToTraverse: Iterable[(PsiElement, PsiElement)] = elements.zip(elementsUnwrapped)
    val rewriteElementsToTraverse: collection.Seq[(PsiElement, collection.Seq[PsiElement])] =
      unwrapPsiFromFormattedElements(rewriteToFormatted)

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

  private def processElementToElementReplace(original: PsiElement,
                                             formatted: PsiElement,
                                             rewriteElement: Option[collection.Seq[PsiElement]],
                                             range: TextRange,
                                             additionalIndent: Int,
                                             elementsToTraverse: mutable.Buffer[(PsiElement, PsiElement)],
                                             res: ListBuffer[PsiChange]
                                            )(implicit project: Project, fileText: String): Unit = {
    (original, formatted) match {
      case (ws1: PsiWhiteSpace, ws2: PsiWhiteSpace) =>
        res += new Replace(ws1, ws2.withAdditionalIndent(additionalIndent))
        return
      case _ =>
    }

    val formattedElementText = formatted.getText
    val originalElementText = getText(original)

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
        case Some(replacement) =>
          // rewriteElements are already unwrapped and do not require additional indent
          res ++= replace(original, replacement, additionalIndent = 0)
        case _ =>
          formatted.accept(new AdjustIndentsVisitor(additionalIndent, project))
          res += new Replace(original, formatted)
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
            if (!originalWs.textMatches(formattedWs.getText)) res += new Replace(originalWs, formattedWs.withAdditionalIndent(additionalIndent))
            originalIndex += 1
            formattedIndex += 1
          case (_, formattedWs: PsiWhiteSpace) => //a whitespace has been added
            res += new Insert(originalElement, formattedWs.withAdditionalIndent(additionalIndent))
            formattedIndex += 1
          case (originalWs: PsiWhiteSpace, _) => //a whitespace has been removed
            res += new Remove(originalWs)
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

  private def replace(element: PsiElement, formattedElements: collection.Seq[PsiElement], additionalIndent: Int): collection.Seq[PsiChange] = {
    val isOneToOneReplacement = formattedElements.size == 1
    if (isOneToOneReplacement && formattedElements.head.textMatches(element.getText)) {
      Seq.empty
    } else {
      formattedElements.foreach(_.accept(new AdjustIndentsVisitor(additionalIndent, element.getProject)))
      if (isOneToOneReplacement) {
        Seq(new Replace(element, formattedElements.head))
      } else {
        formattedElements.map(new Insert(element, _)) :+ new Remove(element)
      }
    }
  }

  private def buildChangesList(elementsToTraverse: Iterable[(PsiElement, PsiElement)],
                               rewriteElementsToTraverse: collection.Seq[(PsiElement, collection.Seq[PsiElement])],
                               range: TextRange, additionalIndent: Int)
                              (implicit project: Project, originalText: String): collection.Seq[PsiChange] = {
    val changes = ListBuffer[PsiChange]()
    fixFirstElementIndent(elementsToTraverse, additionalIndent, changes)

    val elementsBuffer = elementsToTraverse.toBuffer
    while (elementsBuffer.nonEmpty) {
      val (original, formatted) = elementsBuffer.remove(0)
      val rewriteElement = rewriteElementsToTraverse.find(original == _._1).map(_._2)
      processElementToElementReplace(
        original, formatted, rewriteElement, range, additionalIndent, elementsBuffer, changes
      )
    }

    changes
  }

  private def fixFirstElementIndent(elementsToTraverse: Iterable[(PsiElement, PsiElement)],
                                    additionalIndent: Int, changes: ListBuffer[PsiChange])
                                   (implicit project: Project): Unit = {
    for {
      (original, formatted) <- elementsToTraverse.headOption
      prevWsOriginal <- prevSiblingAsWhitespace(original)
      prevWsFormatted <- prevSiblingAsWhitespace(formatted)
    } {
      val indentFormatted = prevWsFormatted.getIndentSize
      val change = prevWsOriginal.withIndent(indentFormatted + additionalIndent) match {
        case Some(prevWsOriginalFixed) => new Replace(prevWsOriginal, prevWsOriginalFixed)
        case None                      => new Remove(prevWsOriginal)
      }
      changes += change
    }
  }

  private def isSameElementType(original: PsiElement, formatted: PsiElement): Boolean = {
    val originalNode = original.getNode
    val formattedNode = formatted.getNode
    originalNode != null && formattedNode != null && originalNode.getElementType == formattedNode.getElementType
  }

  private def applyChanges(changes: collection.Seq[PsiChange], range: TextRange): Int = {
    // changes order: Inserts first, then Replaces and Removes ordered by offset
    val changesFinal = changes.filter(_.isInRange(range)).filter(_.isValid).sorted(Ordering.fromLessThan[PsiChange] {
      case (_: Insert, _) => true
      case (_, _: Insert) => false
      case (left, right)  => left.getStartOffset < right.getStartOffset
    })

    val changesWithNext = changesFinal.zipAll(changesFinal.drop(1), null, null)
    changesWithNext.foldLeft(0) { case (deltaAcc, (change, nextChange)) =>
      val delta = change.applyAndGetDelta(nextChange)
      deltaAcc + delta
    }
  }

  private var failSilent: Boolean = false
  def inFailSilentMode[T](body: => T): T = {
    try {
      failSilent = true
      body
    } finally {
      failSilent = false
    }
  }

  private def reportInvalidCodeFailure(file: PsiFile, error: Option[ScalafmtFormatError] = None)
                                      (implicit project: Project): Unit = {
    import ScalafmtNotifications.displayError

    val fileName = file.getName

    def displayParseError(message: String, offset: Int): Unit = {
      val errorMessage = ScalaBundle.message("scalafmt.format.errors.scala.file.parse.error", fileLink(fileName, offset), message)
      val listener = fileLinkListener(project, file, offset)
      displayError(errorMessage, listener = Some(listener))
    }

    if (ScalaCodeStyleSettings.getInstance(project).SCALAFMT_SHOW_INVALID_CODE_WARNINGS && !failSilent) {
      if (ApplicationManager.getApplication.isUnitTestMode)
        error.map(_.cause).foreach(throw _)

      error.map(_.cause) match {
        case Some(cause: scala.meta.ParseException) =>
          displayParseError(cause.getMessage, cause.pos.start)
        case Some(cause: PositionExceptionImpl) =>
          displayParseError(cause.shortMessage, cause.pos.start)
        case Some(cause) =>
          reportUnknownError(cause)
        case _ =>
          val errorMessage = ScalaBundle.message("scalafmt.format.errors.failed.to.find.correct.surrounding.code", fileLink(fileName))
          val listener = fileLinkListener(project, file, 0)
          displayError(errorMessage, listener = Some(listener))
          if (ApplicationManager.getApplication.isUnitTestMode)
            throw new AssertionError(errorMessage)
      }
    }
  }

  @NonNls private val FileHrefAnchor = "OPEN_FILE"

  @NonNls private def fileLink(fileName: String, offset: Int): String =
    s"""<a href="$FileHrefAnchor">$fileName:$offset</a>"""

  @NonNls private def fileLink(fileName: String): String =
    s"""<a href="$FileHrefAnchor">$fileName</a>"""

  private def fileLinkListener(project: Project, file: PsiFile, offset: Int): NotificationListener =
    (notification: Notification, event: HyperlinkEvent) => {
      notification.expire()
      event.getDescription match {
        case `FileHrefAnchor` =>
          Option(file.getVirtualFile)
            .foreach(new OpenFileDescriptor(project, _, offset).navigate(true))
        case _ =>
      }
    }

  private def reportUnknownError(ex: Throwable): Unit = {
    Log.error("An error occurred during scalafmt formatting", ex)
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

  private val FORMATTED_RANGES_KEY: Key[(TextRanges, Long, Option[Long])] = Key.create("scala.fmt.formatted.ranges")

  private val rangesDeltaCache: mutable.Map[PsiFile, mutable.TreeSet[(Int, Int)]] = mutable.WeakHashMap[PsiFile, mutable.TreeSet[(Int, Int)]]()

  private[scalafmt] def addDelta(offset: Int, containingFile: PsiFile, delta: Int): Int = {
    val cache = rangesDeltaCache.getOrElseUpdate(containingFile, mutable.TreeSet[(Int, Int)]())
    cache.add(offset, delta)
    delta
  }

  private[scalafmt] def addDelta(element: PsiElement, delta: Int): Int =
    addDelta(element.getTextRange.getStartOffset, element.getContainingFile, delta)

  def clearRangesCache(): Unit = {
    rangesDeltaCache.clear()
  }

  private implicit class PsiWhiteSpaceExt(private val ws: PsiWhiteSpace) extends AnyVal {

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

  /**
   * @param filePath used internally by scalafmt to detect overriden config via `fileOverride` option (from v2.5.0)
   */
  private case class ConfigContext(config: ScalafmtReflectConfig, filePath: Option[String]) {
    def withConfig(newConfig: ScalafmtReflectConfig): ConfigContext = this.copy(config = newConfig)
  }

  private implicit class ScalafmtReflectExt(private val scalafmt: ScalafmtReflect) extends AnyVal {

    def tryFormat(code: String)(implicit context: ConfigContext): Either[ScalafmtFormatError, String] =
      Try(scalafmt.format(code, context.config, context.filePath)).toEither.left.map {
        case ReflectionException(e) => ScalafmtFormatError(e)
        case e                      => ScalafmtFormatError(e)
      }
  }
}
