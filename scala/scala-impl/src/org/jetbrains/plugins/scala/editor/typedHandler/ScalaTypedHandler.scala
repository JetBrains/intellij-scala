package org.jetbrains.plugins.scala.editor.typedHandler

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate.Result
import com.intellij.codeInsight.{AutoPopupController, CodeInsightSettings}
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import com.intellij.psi.codeStyle.{CodeStyleManager, CodeStyleSettings}
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.editor.typedHandler.AutoBraceInsertionTools._
import org.jetbrains.plugins.scala.editor.typedHandler.ScalaTypedHandler._
import org.jetbrains.plugins.scala.editor.{AutoBraceAdvertiser, DocumentExt, ScalaEditorUtils, indentElement, indentKeyword}
import org.jetbrains.plugins.scala.extensions.{CharSeqExt, PsiFileExt, _}
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionConfidence
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenTypes, ScalaXmlLexer, ScalaXmlTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterClause
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScVariable, _}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.util.IndentUtil

import java.{util => ju}
import scala.annotation.tailrec
import scala.language.implicitConversions

//noinspection HardCodedStringLiteral
final class ScalaTypedHandler extends TypedHandlerDelegate
  with IndentAdjustor
{

  override def charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result = {
    if (!file.is[ScalaFile])
      return Result.CONTINUE

    val offset = editor.getCaretModel.getOffset
    val document = editor.getDocument

    val element = ScalaEditorUtils.findElementAtCaret_WithFixedEOF(file, document.getTextLength - 1, offset - 1)
    if (element == null)
      return Result.CONTINUE

    if (ScalaDocTypedHandler.isInDocComment(element))
      return Result.CONTINUE

    val documentText = document.getImmutableCharSequence

    // TODO: do not use function literal, use dedicated class with descriptive names
    type Task = (Document, Project, PsiElement, Int) => Unit

    def chooseXmlTask(withAttr: Boolean): Task =
      c match {
        case '>'             => completeXmlTag(tag => "</" + Option(tag.getTagName).getOrElse("") + ">")
        case '/'             => completeEmptyXmlTag(editor)
        case '=' if withAttr => completeXmlAttributeQuote(editor)
        case _               => null
      }

    // TODO: we can avoid allocations by comparing strings inplace, without substring
    @inline
    def hasPrefix(prefix: String): Boolean =
      prefix.length <= offset && offset <= documentText.length &&
        documentText.substring(offset - prefix.length, offset) == prefix

    val myTask: Task = if (c == ' ' && hasPrefix(" case ")) {
      indentKeyword[ScCaseClause](ScalaTokenTypes.kCASE, file)
    } else if (c == ' ' && hasPrefix("else ")) {
      indentKeyword[ScIf](ScalaTokenTypes.kELSE, file)
    } else if (c == ' ' && hasPrefix("catch ")) {
      indentKeyword[ScCatchBlock](ScalaTokenTypes.kCATCH, file)
    } else if (c == ' ' && hasPrefix("finally ")) {
      indentKeyword[ScFinallyBlock](ScalaTokenTypes.kFINALLY, file)
    } else if (c == '{' && hasPrefix(" {")) {
      indentValBraceStyle(file)
    } else if (isInPlace(element, classOf[ScXmlExpr], classOf[ScXmlPattern])) {
      chooseXmlTask(withAttr = true)
    } else if (file.findElementAt(offset - 2) match {
      case el: PsiElement if !ScalaNamesUtil.isOperatorName(el.getText) && !el.textMatches("=") =>
        c == '>' || c == '/'
      case _ => false
    }) {
      chooseXmlTask(withAttr = false)
    } else if (element.getPrevSibling != null && element.getPrevSibling.getNode.getElementType == ScalaElementType.CASE_CLAUSES) {
      val ltIndex = element.getPrevSibling.getText.indexOf("<")
      if (ltIndex > "case ".length - 1 && element.getPrevSibling.getText.substring(0, ltIndex).trim == "case") {
        chooseXmlTask(withAttr = false)
      } else {
        null
      }
    } else if (c == '{' && (element.getParent match {
      case l: ScInterpolatedStringLiteral => !l.isMultiLineString
      case _ => false
    })) {
      completeInterpolatedStringBraces
    } else if (c == '>' || c == '-') {
      replaceArrowTask()
    } else if (c == '$') {
      startAutoPopupCompletion(file, editor)
    } else if (c == '{') {
      convertToInterpolated(file)
    } else if (c == '.' && shouldAdjustIndentAfterDot(editor)) {
      prepareIndentAdjustmentBeforeDot(document, offset)
      adjustIndentBeforeDot(editor)
    }
    //SCL-18951
    else if (c != '.' && c != ' ' && shouldAdjustIndentBecauseOfPostfix(offset, element, document, editor)) {
      adjustIndent
    } else if (c == '.') {
      startAutoPopupCompletionInInterpolatedString(file, editor)
    } else if (offset > 1) {
      val prevPositionElement = file.findElementAt(offset - 2)
      if (ScalaPsiUtil.isLineTerminator(prevPositionElement)) {
        val prevSibling = ScalaTypedHandler.getPrevSiblingCondition(prevPositionElement)
        prevSibling.map(_.getNode.getElementType).orNull match {
          case ScalaTokenTypes.tDOT       => indentRefExprDot(file)
          case ScalaTokenTypes.tCOMMA     => indentParametersComma(file)
          case ScalaTokenTypes.tASSIGN    => indentDefinitionAssign(file)
          case ScalaTokenTypes.tSEMICOLON => indentForGenerators(file)
          case _                          => null
        }
      } else null
    } else null

    if (myTask == null) {
      Result.CONTINUE
    } else {
      document.commit(project)
      myTask(document, project, file.findElementAt(offset - 1), offset) // prev element is not valid here
      Result.STOP
    }
  }

  override def beforeCharTyped(c: Char, project: Project, editor: Editor, file: PsiFile, fileType: FileType): Result = {
    if (!file.is[ScalaFile])
      return Result.CONTINUE

    implicit val e: Editor = editor
    implicit val p: Project = project

    val offset = editor.getCaretModel.getOffset
    val prevElement = file.findElementAt(offset - 1)
    val element = file.findElementAt(offset)
    if (element == null)
      return Result.CONTINUE
    if (ScalaDocTypedHandler.isInDocComment(element))
      return Result.CONTINUE

    val elementType = element.getNode.getElementType

    implicit val f: PsiFile = file
    implicit val settings: CodeStyleSettings = CodeStyle.getSettings(project)
    val scalaSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    lazy val smartKeySettings = ScalaApplicationSettings.getInstance

    def moveCaret(): Unit = {
      editor.getCaretModel.moveCaretRelatively(1, 0, false, false, false)
    }

    // see SCL-22278
    if (c == '.') scheduleAutoPopup(file, editor, project)

    if (c == '"' && elementType == ScalaXmlTokenTypes.XML_ATTRIBUTE_VALUE_END_DELIMITER) {
      moveCaret()
      Result.STOP
    } else if ((c == '>' || c == '/') && elementType == ScalaXmlTokenTypes.XML_EMPTY_ELEMENT_END) {
      moveCaret()
      Result.STOP
    } else if (c == '>' && elementType == ScalaXmlTokenTypes.XML_TAG_END) {
      moveCaret()
      Result.STOP
    } else if (c == '>' && prevElement != null && prevElement.getNode.getElementType == ScalaXmlTokenTypes.XML_EMPTY_ELEMENT_END) {
      Result.STOP
    } else if (c == '>' && scalaSettings.REPLACE_CASE_ARROW_WITH_UNICODE_CHAR && prevElement != null &&
      prevElement.getNode.getElementType == ScalaTokenTypes.tFUNTYPE) {
      Result.STOP
    } else if (c == '{' && smartKeySettings.WRAP_SINGLE_EXPRESSION_BODY) {
      handleLeftBraceWrap(offset, element)
    } else if (shouldHandleAutoBracesBeforeTyped(c)) {
      findAutoBraceInsertionOpportunity(Some(c), offset, element)
        .fold(Result.CONTINUE) { info =>
          insertAutoBraces(info)
          // prevent other beforeTyped-handlers from being executed because psi tree is out of sync now
          Result.DEFAULT
        }
    } else if (c.isWhitespace) {
      findAutoBraceInsertionOpportunityWhenStartingStatement(c, offset, element)
        .fold(Result.CONTINUE) { info =>
          insertAutoBraces(info)
          // prevent other beforeTyped-handlers from being executed because psi tree is out of sync now
          Result.DEFAULT
        }
    } else {
      Result.CONTINUE
    }
  }

  override def checkAutoPopup(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result = {
    if (!file.is[ScalaFile]) Result.CONTINUE
    else if (charTyped == '{' || charTyped == '[') {
      AutoPopupController.getInstance(project).autoPopupParameterInfo(editor, null)
      Result.STOP
    } else Result.CONTINUE
  }

  private def isInPlace(element: PsiElement, place: Class[_ <: PsiElement]*): Boolean = {
    if (element == null || place == null)
      return false

    var nextParent = element.getParent
    while (nextParent != null) {
      if (place.exists(_.isAssignableFrom(nextParent.getClass)))
        return true
      nextParent = nextParent.getParent
    }
    false
  }

  private def completeXmlTag(insert: ScXmlStartTag => String)(document: Document, project: Project, element: PsiElement, offset: Int): Unit = {
    if (element == null) return

    def doInsert(tag: ScXmlStartTag): Unit =
      insertAndCommit(offset, insert(tag), document, project)

    def check(tag: ScXmlStartTag): Unit =
      if (Option(tag.getClosingTag).forall(_.getTagName != tag.getTagName))
        doInsert(tag)

    element.getParent match {
      case tag: ScXmlStartTag =>
        if (tag.getParent != null && tag.getParent.getParent != null) {
          tag.getParent.getParent.getFirstChild match {
            case st: ScXmlStartTag if st.getTagName == tag.getTagName => doInsert(tag)
            case _ => check(tag)
          }
        } else {
          check(tag)
        }
      case _ =>
    }
  }

  private def completeInterpolatedStringBraces(document: Document, project: Project, element: PsiElement, offset: Int): Unit = {
    if (element == null)
      return
    import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._

    val needInsert = CodeInsightSettings.getInstance.AUTOINSERT_PAIR_BRACKET &&
      element.elementType == tLBRACE &&
      element.getParent.prevSibling.exists(_.elementType == tINTERPOLATED_STRING_INJECTION) &&
      element.nextSibling.forall(_.elementType != tRBRACE)
    if (needInsert) {
      insertAndCommit(offset, "}", document, project)
    }
  }

  private def completeXmlAttributeQuote(editor: Editor)(document: Document, project: Project, element: PsiElement, offset: Int): Unit =
    if (element != null && element.getNode.getElementType == ScalaXmlTokenTypes.XML_EQ && element.getParent != null &&
      element.getParent.is[ScXmlAttribute]) {
      insertAndCommit(offset, "\"\"", document, project)
      editor.getCaretModel.moveCaretRelatively(1, 0, false, false, false)
    }

  private def insertAndCommit(offset: Int, text: String, document: Document, project: Project): Unit = {
    document.insertString(offset, text)
    document.commit(project)
  }

  private val NoMatter: PsiElement => Boolean = _ => true

  private def indentRefExprDot(file: PsiFile)(document: Document, project: Project, element: PsiElement, offset: Int): Unit =
    indentElement(file, checkVisibleOnly = false)(document, project, element, offset)(
      NoMatter,
      elem => elem.getParent.is[ScReferenceExpression]
    )

  private def indentParametersComma(file: PsiFile)(document: Document, project: Project, element: PsiElement, offset: Int): Unit =
    indentElement(file, checkVisibleOnly = false)(document, project, element, offset)(
      NoMatter,
      ScalaPsiUtil.getParent(_, 2).exists(_.is[ScParameterClause, ScArgumentExprList])
    )

  private def indentDefinitionAssign(file: PsiFile)(document: Document, project: Project, element: PsiElement, offset: Int): Unit =
    indentElement(file, checkVisibleOnly = false)(document, project, element, offset)(
      NoMatter,
      ScalaPsiUtil.getParent(_, 2)
        .exists(_.is[ScFunction, ScVariable, ScValue, ScTypeAlias])
    )

  private def indentForGenerators(file: PsiFile)(document: Document, project: Project, element: PsiElement, offset: Int): Unit =
    indentElement(file)(document, project, element, offset)(
      ScalaPsiUtil.isLineTerminator,
      ScalaPsiUtil.getParent(_, 3).exists(_.is[ScEnumerators])
    )

  private def indentValBraceStyle(file: PsiFile)(document: Document, project: Project, element: PsiElement, offset: Int): Unit =
    indentElement(file)(document, project, element, offset)(
      ScalaPsiUtil.isLineTerminator,
      ScalaPsiUtil.getParent(_, 2).exists(_.is[ScValue])
    )

  private def replaceArrowTask()(document: Document, project: Project, element: PsiElement, offset: Int): Unit = {
    @inline def replaceElement(replaceWith: String): Unit = {
      document.replaceString(element.startOffset, element.endOffset, replaceWith)
      document.commit(project)
    }

    val settings = ScalaCodeStyleSettings.getInstance(project)

    element.getNode.getElementType match {
      case ScalaTokenTypes.tFUNTYPE if settings.REPLACE_CASE_ARROW_WITH_UNICODE_CHAR =>
        replaceElement(ScalaTypedHandler.unicodeCaseArrow)
      case ScalaTokenTypes.tIDENTIFIER if settings.REPLACE_MAP_ARROW_WITH_UNICODE_CHAR && element.textMatches("->") =>
        replaceElement(ScalaTypedHandler.unicodeMapArrow)
      case ScalaTokenTypes.tCHOOSE if settings.REPLACE_FOR_GENERATOR_ARROW_WITH_UNICODE_CHAR =>
        replaceElement(ScalaTypedHandler.unicodeForGeneratorArrow)
      case _ =>
    }
  }

  private def startAutoPopupCompletion(file: PsiFile, editor: Editor)(document: Document, project: Project, element: PsiElement, offset: Int): Unit =
    if (CodeInsightSettings.getInstance().AUTO_POPUP_COMPLETION_LOOKUP) {
      element.getParent match {
        case l: ScLiteral =>
          element.getNode.getElementType match {
            case ScalaTokenTypes.tSTRING | ScalaTokenTypes.tMULTILINE_STRING =>
              if (l.getText.count(_ == '$') == 1)
                scheduleAutoPopup(file, editor, project)
            case _ =>
          }
        case _ =>
      }
    }

  private def scheduleAutoPopup(file: PsiFile, editor: Editor, project: Project): Unit =
    AutoPopupController.getInstance(project).scheduleAutoPopup(
      editor, CompletionType.BASIC, (t: PsiFile) => t == file
    )

  private def startAutoPopupCompletionInInterpolatedString(file: PsiFile, editor: Editor)
                                                          (document: Document, project: Project, element: PsiElement, offset: Int): Unit =
    if (CodeInsightSettings.getInstance().AUTO_POPUP_COMPLETION_LOOKUP) {
      element.getParent match {
        case _: ScLiteral =>
          element.getNode.getElementType match {
            case ScalaTokenTypes.tINTERPOLATED_STRING | ScalaTokenTypes.tINTERPOLATED_MULTILINE_STRING =>
              if (ScalaCompletionConfidence.isDotTypedAfterStringInjectedReference(file, offset)) {
                scheduleAutoPopup(file, editor, project)
              }
            case _ =>
          }
        case _ =>
      }
    }

  private def convertToInterpolated(file: PsiFile)(document: Document, project: Project, element: PsiElement, offset: Int): Unit =
    if (ScalaApplicationSettings.getInstance().UPGRADE_TO_INTERPOLATED) {
      element.getParent match {
        case l: ScLiteral =>
          element.getNode.getElementType match {
            case ScalaTokenTypes.tSTRING | ScalaTokenTypes.tMULTILINE_STRING =>
              val chars = file.charSequence
              if (l.getText.count(_ == '$') == 1 && chars.charAt(offset - 2) == '$') {
                if (chars.charAt(offset) != '}' && CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) {
                  document.insertString(offset, "}")
                }
                document.insertString(l.startOffset, "s")
                document.commit(project)
              }
            case _ =>
          }
        case _ =>
      }
    }

  private def completeEmptyXmlTag(editor: Editor)(document: Document, project: Project, element: PsiElement, offset: Int): Unit =
    if (element != null && element.getNode.getElementType == ScalaXmlTokenTypes.XML_DATA_CHARACTERS && element.textMatches("/") &&
      element.getPrevSibling != null && element.getPrevSibling.is[ScXmlStartTag]) {
      val xmlLexer = new ScalaXmlLexer
      xmlLexer.start(element.getPrevSibling.getText + "/>")
      xmlLexer.advance()

      if (xmlLexer.getTokenType != ScalaXmlTokenTypes.XML_START_TAG_START) return

      while (xmlLexer.getTokenEnd < xmlLexer.getBufferEnd) {
        xmlLexer.advance()
      }

      if (xmlLexer.getTokenType != ScalaXmlTokenTypes.XML_EMPTY_ELEMENT_END) return

      document.insertString(offset, ">")
      editor.getCaretModel.moveCaretRelatively(1, 0, false, false, false)
      document.commit(project)
    }

  private def handleLeftBraceWrap(caretOffset: Int, element: PsiElement)
                                 (implicit project: Project, file: PsiFile, editor: Editor, settings: CodeStyleSettings): Result =
    findElementToWrap(element) match {
      case Some(wrapInfo) =>
        if (!wrapInfo.wrapElement.is[ScBlockExpr])
          wrapWithBraces(caretOffset, wrapInfo)
        else Result.CONTINUE
      case _ => Result.CONTINUE
    }

  private def wrapWithBraces(caretOffset: Int, wrap: BraceWrapInfo)
                            (implicit project: Project, file: PsiFile, editor: Editor, settings: CodeStyleSettings): Result = {
    val document = editor.getDocument

    val BraceWrapInfo(wrapElement, beforeCaret, parent, canShareLine) = wrap

    val elementStartOffset = wrapElement.startOffset
    val elementLine = document.getLineNumber(elementStartOffset)
    val prevElementLine = document.getLineNumber(beforeCaret.startOffset)
    val caretLine = document.getLineNumber(caretOffset)

    val caretIsBeforeElement = caretOffset <= elementStartOffset
    val caretAndPrevElementOnSameLine = caretLine == prevElementLine
    val singleLineDefinition = prevElementLine == elementLine

    val needToHandle = caretIsBeforeElement &&
      caretAndPrevElementOnSameLine &&
      isElementIndented(parent, wrapElement) &&
      !followedByBrokenClosingBrace(wrapElement)
    if (needToHandle) {
      document.insertString(caretOffset, "{")

      if (singleLineDefinition) {
        // if left brace is inserted on the same line with body we expect the user to press Enter after that
        // in this case we rely that EnterAfterUnmatchedBraceHandler will insert missing closing brace
        editor.getCaretModel.moveToOffset(caretOffset + 1)
      }
      else {
        val endElement = advanceElementToLineComment(wrapElement)
        val elementActualStartOffset = elementStartOffset + 1
        val elementActualEndOffset = endElement.endOffset + 1
        editor.getCaretModel.moveToOffset(elementActualStartOffset)

        if (canShareLine) {
          endElement.getNextSibling match {
            case ws@Whitespace(wsText) =>
              val lastNewLineIdx = wsText.lastIndexOf("\n").max(0)
              document.deleteString(ws.startOffset + lastNewLineIdx + 1, ws.endOffset + 1)
            case _ =>
          }
        }
        val insertedClosingBraceWithNl = "\n}"
        document.insertString(elementActualEndOffset, insertedClosingBraceWithNl)
        document.commit(project)

        val ranges = ju.Arrays.asList(
          TextRange.from(caretOffset, 1),
          //NOTES:
          // extra `+1` is required to cover space after `}`
          // e.g. we want a space to be inserted before `else` in `if (...) {...} else` when `}` is inserted
          // (it's actual for many other constructs, see SCL-15549)
          // `.min(document.getTextLength)` is required inn order range doesn't exceed document length,
          // in case it's simple block without any continuation
          TextRange.create(elementActualEndOffset, (elementActualEndOffset + insertedClosingBraceWithNl.length + 1).min(document.getTextLength))
        )
        CodeStyleManager.getInstance(project).reformatText(file, ranges)
      }

      AutoBraceAdvertiser.advertiseAutoBraces(project)
      Result.STOP
    }
    else {
      Result.CONTINUE
    }
  }

  /** @return line comment following the element if exists or original element */
  private def advanceElementToLineComment(element: PsiElement): PsiElement = {
    @tailrec
    def inner(el: PsiElement): PsiElement = {
      PsiTreeUtil.nextLeaf(el) match {
        case comment: PsiComment if comment.elementType == ScalaTokenTypes.tLINE_COMMENT => comment
        case ws@Whitespace(text) if !text.contains("\n") => inner(ws)
        case _ => element
      }
    }

    inner(element)
  }

  private def isElementIndented(parent: PsiElement, child: PsiElement)(implicit settings: CodeStyleSettings): Boolean = {
    val tabSize = settings.getTabSize(ScalaFileType.INSTANCE)
    IndentUtil.compare(child, parent, tabSize) > 0
  }

  private def followedByBrokenClosingBrace(element: PsiElement): Boolean = {
    val next = element.getNextNonWhitespaceAndNonEmptyLeaf
    val isClosingBrace = next != null && next.elementType == ScalaTokenTypes.tRBRACE
    isClosingBrace && PsiTreeUtil.nextLeaf(next).is[PsiErrorElement]
  }
}

object ScalaTypedHandler {

  val unicodeCaseArrow = "⇒"
  val unicodeMapArrow = "→"
  val unicodeForGeneratorArrow = "←"

  private def getPrevSiblingCondition(element: PsiElement): Option[PsiElement] = {
    var prev: PsiElement = PsiTreeUtil.prevLeaf(element)
    while (prev != null && prev.getTextLength == 0) {
      prev = prev.getPrevSibling
    }
    Option(prev)
  }

  /**
   * @param wrapElement  element after  which closing brace should be inserted (or removed)
   * @param beforeCaret  element prior to the caret position just after typing or removing brace
   * @param parent       element against which we should check that `wrap` element is indented
   * @param canShareLine whether closing brace can be inserted just before some other element with the same parent
   *                     for example in:
   *                     if(...) {
   *                     } else {
   *                     }
   *                     closing brace for if is inserted just right before else
   * @see [[org.jetbrains.plugins.scala.editor.backspaceHandler.ScalaBackspaceHandler.handleLeftBrace]]
   */
  case class BraceWrapInfo(wrapElement: PsiElement, beforeCaret: PsiElement, parent: PsiElement, canShareLine: Boolean = false)

  /**
   * @param element element at caret position
   */
  def findElementToWrap(element: PsiElement): Option[BraceWrapInfo] = {
    val prevElement: PsiElement = PsiTreeUtil.prevLeaf(element) match {
      case ws: PsiWhiteSpace => PsiTreeUtil.prevLeaf(ws)
      case prev => prev
    }
    if (prevElement == null || prevElement.elementType == ScalaTokenTypes.tLBRACE)
      return None

    val parent: PsiElement = prevElement.getParent

    @inline def wrap(el: PsiElement, share: Boolean = false) =
      BraceWrapInfo(el, prevElement, firstElementOnTheLine(parent), share)

    // to evaluate wrapElement lazily and not to write `() => obj.body` everywhere
    @inline implicit def oToFo(el: Option[PsiElement]): () => Option[PsiElement] = () => el

    //noinspection NameBooleanParameters
    val tuple: (PsiElement => Boolean, () => Option[PsiElement], PsiElement => BraceWrapInfo) = parent match {
      case funDef: ScFunctionDefinition => (funDef.assignment.contains, funDef.body, wrap(_))
      case varDef: ScVariableDefinition => (varDef.assignment.contains, varDef.expr, wrap(_))
      case patDef: ScPatternDefinition  => (patDef.assignment.contains, patDef.expr, wrap(_))
      case d: ScDo                      => (d.getFirstChild.eq, d.body, wrap(_, true))
      case w: ScWhile                   => (w.rightParen.contains, w.expression, wrap(_))
      case t: ScTry                     => (t.getFirstChild.eq, t.expression, wrap(_, true))
      case f: ScFor                     => (f.yieldOrDoKeyword.orElse(f.getRightBracket).contains, f.body, wrap(_))
      case ifExpr: ScIf =>
        if (ifExpr.rightParen.contains(prevElement))
          (_ => true, ifExpr.thenExpression, wrap(_, ifExpr.elseKeyword.isDefined))
        else
          (ifExpr.elseKeyword.contains, ifExpr.elseExpression, wrap(_))
      case f: ScFinallyBlock => (
        f.getFirstChild.eq,
        f.getFirstChild.getNextSiblingNotWhitespace.toOption,
        BraceWrapInfo(_, prevElement, firstElementOnTheLine(f.getParent))
      )
      case _ => (_ => false, None, _ => null)
    }

    val (prevElementChecker, elementToWrap, wrapper) = tuple
    if (prevElementChecker(prevElement)) {
      val toWrap = elementToWrap()
      toWrap.map(wrapper)
    } else {
      None
    }
  }

  @scala.annotation.tailrec
  private def firstElementOnTheLine(el: PsiElement): PsiElement =
    if (el.startsFromNewLine()) el else {
      PsiTreeUtil.prevLeaf(el, true) match {
        case null => el
        case prev => firstElementOnTheLine(prev)
      }
    }

}
