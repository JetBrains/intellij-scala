package org.jetbrains.plugins.scala.editor.typedHandler

import java.{util => ju}

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate.Result
import com.intellij.codeInsight.{AutoPopupController, CodeInsightSettings}
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi._
import com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions
import com.intellij.psi.codeStyle.{CodeStyleManager, CodeStyleSettings}
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.editor.typedHandler.ScalaTypedHandler._
import org.jetbrains.plugins.scala.editor.{AutoBraceUtils, DocumentExt, EditorExt}
import org.jetbrains.plugins.scala.extensions.{CharSeqExt, PsiFileExt, _}
import org.jetbrains.plugins.scala.highlighter.ScalaCommenter
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
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterClause
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.scaladoc.ScalaIsCommentComplete
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.docsyntax.ScalaDocSyntaxElementType
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.util.IndentUtil
import org.jetbrains.plugins.scala.{ScalaFileType, ScalaLanguage}

import scala.annotation.tailrec
import scala.language.implicitConversions

//noinspection HardCodedStringLiteral
final class ScalaTypedHandler extends TypedHandlerDelegate {

  override def charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result = {
    if (!file.isInstanceOf[ScalaFile]) return Result.CONTINUE

    val offset = editor.offset
    val element = file.findElementAt(offset - 1)
    //if (element == null) return Result.CONTINUE

    val document = editor.getDocument
    val text = document.getImmutableCharSequence

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
      prefix.length <= offset && offset <= text.length &&
        text.substring(offset - prefix.length, offset) == prefix

    val myTask: Task = if (element != null && isInDocComment(element)) { //we don't have to check offset >= 3 because "/**" is already has 3 characters
      getScaladocTask(text, offset)
    } else if (c == ' ' && hasPrefix(" case ")) {
      indentCase(file)
    } else if (c == ' ' && hasPrefix("else ")) {
      indentElse(file)
    } else if (c == '{' && hasPrefix(" {")) {
      indentValBraceStyle(file)
    } else if (element != null && isInPlace(element, classOf[ScXmlExpr], classOf[ScXmlPattern])) {
      chooseXmlTask(withAttr = true)
    } else if (file.findElementAt(offset - 2) match {
      case el: PsiElement if !ScalaNamesUtil.isOperatorName(el.getText) && !el.textMatches("=") =>
        c == '>' || c == '/'
      case _ => false
    }) {
      chooseXmlTask(withAttr = false)
    } else if (element != null && element.getPrevSibling != null && element.getPrevSibling.getNode.getElementType == ScalaElementType.CASE_CLAUSES) {
      val ltIndex = element.getPrevSibling.getText.indexOf("<")
      if (ltIndex > "case ".length - 1 && element.getPrevSibling.getText.substring(0, ltIndex).trim == "case") {
        chooseXmlTask(withAttr = false)
      } else {
        null
      }
    } else if (element != null && c == '{' && (element.getParent match {
      case l: ScInterpolatedStringLiteral => !l.isMultiLineString
      case _ => false
    })) {
      completeInterpolatedStringBraces
    } else if (c == '>' || c == '-') {
      replaceArrowTask(file, editor)
    } else if (c == '$') {
      startAutoPopupCompletion(file, editor)
    } else if (c == '{') {
      convertToInterpolated(file, editor)
    } else if (c == '.' && isSingleCharOnLine(editor)) {
      addContinuationIndentBeforeDot(CodeStyle.getLanguageSettings(file, ScalaLanguage.INSTANCE).getIndentOptions)
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
    if (!file.isInstanceOf[ScalaFile]) return Result.CONTINUE

    implicit val e: Editor = editor
    implicit val p: Project = project

    val offset = editor.offset
    val prevElement = file.findElementAt(offset - 1)
    val element = file.findElementAt(offset)
    if (element == null) return Result.CONTINUE

    val elementType = element.getNode.getElementType

    implicit val f: PsiFile = file
    implicit val settings: CodeStyleSettings = CodeStyle.getSettings(project)
    val scalaSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    lazy val smartKeySettings = ScalaApplicationSettings.getInstance

    def moveCaret(): Unit = {
      editor.getCaretModel.moveCaretRelatively(1, 0, false, false, false)
    }

    if (c == ' ' && prevElement != null && needClosingScaladocTag(element, prevElement)) {
      insertClosingScaladocTag(offset, element)
      moveCaret()
      Result.STOP
    } else if (isClosingScaladocTagOrMarkup(c, element, elementType)) {
      moveCaret()
      Result.STOP
    } else if (c == '"' && elementType == ScalaXmlTokenTypes.XML_ATTRIBUTE_VALUE_END_DELIMITER) {
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
    } else if (smartKeySettings.HANDLE_BLOCK_BRACES_AUTOMATICALLY && !c.isWhitespace && c != '{' && c != '}') {
      handleAutoBraces(c, offset, element)
    } else {
      Result.CONTINUE
    }
  }

  @inline
  private def isClosingScaladocTagOrMarkup(c: Char, element: PsiElement, elementType: IElementType) = {
    (elementType.isInstanceOf[ScalaDocSyntaxElementType] || elementType == ScalaDocTokenType.DOC_INNER_CLOSE_CODE_TAG) &&
      isInDocComment(element) &&
      element.getParent.getLastChild == element && element.getText.startsWith("" + c) &&
      // handling case when '`' was type right after second '`' inside "````" to enable bold syntax ("```bold text```)"
      !(elementType == ScalaDocTokenType.DOC_ITALIC_TAG &&
        element.getPrevSibling.nullSafe.map(_.getNode.getElementType).get == ScalaDocTokenType.DOC_ITALIC_TAG)
  }

  @inline
  private def isInDocComment(element: PsiElement): Boolean = isInPlace(element, classOf[ScDocComment])

  private def needClosingScaladocTag(element: PsiElement, prevElement: PsiElement)(implicit editor: Editor): Boolean =
    prevElement.elementType == ScalaDocTokenType.DOC_COMMENT_START && (prevElement.getParent match {
      case comment: ScDocComment =>
        val isAtNewLine = element match {
          case DocWhitespace(ws) => ws.contains("\n")
          case _ => false
        }
        isAtNewLine && !ScalaIsCommentComplete.isCommentComplete(comment, ScalaCommenter, editor)
      case _ =>
        false
    })

  private def insertClosingScaladocTag(offset: Int, element: PsiElement)(implicit editor: Editor): Unit = {
    val docEnd = ScalaCommenter.getDocumentationCommentSuffix
    insertAndCommit(offset, "  " + docEnd, editor.getDocument, editor.getProject)
  }

  private def isInPlace(element: PsiElement, place: Class[_ <: PsiElement]*): Boolean = {
    if (element == null || place == null) return false

    var nextParent = element.getParent
    while (nextParent != null) {
      if (place.exists(_.isAssignableFrom(nextParent.getClass))) return true
      nextParent = nextParent.getParent
    }
    false
  }

  private def completeScalaDocWikiSyntax(tagToInsert: String)(document: Document, project: Project, element: PsiElement, offset: Int): Unit =
    if (element.getNode.getElementType.isInstanceOf[ScalaDocSyntaxElementType] || tagToInsert == "}}}") {
      insertAndCommit(offset, tagToInsert, document, project)
    }

  private def completeScalaDocBoldSyntaxElement(document: Document, project: Project, element: PsiElement, offset: Int): Unit =
    if (element.getNode.getElementType.isInstanceOf[ScalaDocSyntaxElementType]) {
      insertAndCommit(offset, "'", document, project)
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
    if (element == null) return
    import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._

    if (element.getNode.getElementType == tLBRACE &&
      Option(element.getParent.getPrevSibling).exists(_.getNode.getElementType == tINTERPOLATED_STRING_INJECTION) &&
      (element.getNextSibling == null || element.getNextSibling.getNode.getElementType != tRBRACE)) {
      insertAndCommit(offset, "}", document, project)
    }
  }

  private def completeXmlAttributeQuote(editor: Editor)(document: Document, project: Project, element: PsiElement, offset: Int): Unit =
    if (element != null && element.getNode.getElementType == ScalaXmlTokenTypes.XML_EQ && element.getParent != null &&
      element.getParent.isInstanceOf[ScXmlAttribute]) {
      insertAndCommit(offset, "\"\"", document, project)
      editor.getCaretModel.moveCaretRelatively(1, 0, false, false, false)
    }

  private def insertAndCommit(offset: Int, text: String, document: Document, project: Project): Unit = {
    document.insertString(offset, text)
    document.commit(project)
  }

  private def getScaladocTask(text: CharSequence, offset: Int): (Document, Project, PsiElement, Int) => Unit = {
    if (offset < 3 || text.length <= offset) return null

    text.charAt(offset) match {
      case ' ' | '\n' | '\t' | '\r' | '\'' =>
      case _ => return null
    }

    if (text.substring(offset - 3, offset) == "'''") {
      completeScalaDocBoldSyntaxElement
    } else if (wiki1LTagMatch.contains(text.substring(offset - 1, offset))) {
      completeScalaDocWikiSyntax(text.substring(offset - 1, offset))
    } else if (wiki2LTagMatch.contains(text.substring(offset - 2, offset))) {
      completeScalaDocWikiSyntax(wiki2LTagMatch(text.substring(offset - 2, offset)))
    } else if (text.substring(offset - 3, offset) == "{{{") {
      completeScalaDocWikiSyntax("}}}")
    } else {
      null
    }
  }

  private val NoMatter: PsiElement => Boolean = _ => true

  private def indentCase(file: PsiFile)(document: Document, project: Project, element: PsiElement, offset: Int): Unit =
    indentElement(file)(document, project, element, offset)(
      elem => elem.getNode.getElementType == ScalaTokenTypes.kCASE && elem.getParent.isInstanceOf[ScCaseClause]
    )

  private def indentElse(file: PsiFile)(document: Document, project: Project, element: PsiElement, offset: Int): Unit =
    indentElement(file)(document, project, element, offset)(
      elem => elem.getNode.getElementType == ScalaTokenTypes.kELSE && elem.getParent.isInstanceOf[ScIf]
    )

  private def indentRefExprDot(file: PsiFile)(document: Document, project: Project, element: PsiElement, offset: Int): Unit =
    indentElement(file)(document, project, element, offset)(
      NoMatter,
      elem => elem.getParent.isInstanceOf[ScReferenceExpression]
    )

  private def indentParametersComma(file: PsiFile)(document: Document, project: Project, element: PsiElement, offset: Int): Unit =
    indentElement(file)(document, project, element, offset)(
      NoMatter,
      ScalaPsiUtil.getParent(_, 2).exists {
        case _: ScParameterClause | _: ScArgumentExprList => true
        case _ => false
      }
    )

  private def indentDefinitionAssign(file: PsiFile)(document: Document, project: Project, element: PsiElement, offset: Int): Unit =
    indentElement(file)(document, project, element, offset)(
      NoMatter,
      ScalaPsiUtil.getParent(_, 2).exists {
        case _: ScFunction | _: ScVariable | _: ScValue | _: ScTypeAlias => true
        case _ => false
      }
    )

  private def indentForGenerators(file: PsiFile)(document: Document, project: Project, element: PsiElement, offset: Int): Unit =
    indentElement(file)(document, project, element, offset)(
      ScalaPsiUtil.isLineTerminator,
      ScalaPsiUtil.getParent(_, 3).exists(_.isInstanceOf[ScEnumerators])
    )

  private def indentValBraceStyle(file: PsiFile)(document: Document, project: Project, element: PsiElement, offset: Int): Unit =
    indentElement(file)(document, project, element, offset)(
      ScalaPsiUtil.isLineTerminator,
      ScalaPsiUtil.getParent(_, 2).exists(_.isInstanceOf[ScValue])
    )

  private def indentElement(
    file: PsiFile
  )(
    document: Document,
    project: Project,
    element: PsiElement,
    offset: Int
  )(
    prevCondition: PsiElement => Boolean,
    condition: PsiElement => Boolean = _.isInstanceOf[PsiWhiteSpace]
  ): Unit =
    if (condition(element)) {
      val anotherElement = file.findElementAt(offset - 2)
      if (prevCondition(anotherElement)) {
        document.commit(project)
        CodeStyleManager.getInstance(project).adjustLineIndent(file, anotherElement.getTextRange)
      }
    }

  private def addContinuationIndentBeforeDot(indentOptions: IndentOptions)
                                            (document: Document, project: Project, element: PsiElement, offset: Int): Unit = {
    val file = element.getContainingFile

    val dotOffset = offset - 1
    val baseIndent = CodeStyleManager.getInstance(project).getLineIndent(file, dotOffset)

    val extraIndentSize = indentOptions.CONTINUATION_INDENT_SIZE
    val indentString =
      if (indentOptions.USE_TAB_CHARACTER)
        IndentUtil.appendSpacesToIndentString(baseIndent, extraIndentSize, indentOptions.TAB_SIZE)
      else
        baseIndent + StringUtil.repeatSymbol(' ', extraIndentSize)

    val lineStartOffset = document.lineStartOffset(dotOffset)
    document.replaceString(lineStartOffset, dotOffset, indentString)
    document.commit(project)
  }

  private def isSingleCharOnLine(editor: Editor): Boolean = {
    val document = editor.getDocument
    val offset = editor.offset
    val lineStart = document.lineStartOffset(offset)

    val prefix =
      if (lineStart < offset)
        document.getImmutableCharSequence.substring(lineStart, offset - 1)
      else ""
    val suffix = document.getImmutableCharSequence.substring(offset, document.lineEndOffset(offset))

    (prefix + suffix).forall(_.isWhitespace)
  }

  private def replaceArrowTask(file: PsiFile, editor: Editor)(document: Document, project: Project, element: PsiElement, offset: Int): Unit = {
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
              if (l.getText.filter(_ == '$').length == 1)
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

  private def convertToInterpolated(file: PsiFile, editor: Editor)(document: Document, project: Project, element: PsiElement, offset: Int): Unit =
    if (ScalaApplicationSettings.getInstance().UPGRADE_TO_INTERPOLATED) {
      element.getParent match {
        case l: ScLiteral =>
          element.getNode.getElementType match {
            case ScalaTokenTypes.tSTRING | ScalaTokenTypes.tMULTILINE_STRING =>
              val chars = file.charSequence
              if (l.getText.filter(_ == '$').length == 1 && chars.charAt(offset - 2) == '$') {
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
      element.getPrevSibling != null && element.getPrevSibling.isInstanceOf[ScXmlStartTag]) {
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

  private def handleAutoBraces(c: Char, caretOffset: Int, element: PsiElement)
                              (implicit project: Project, file: PsiFile, editor: Editor, settings: CodeStyleSettings): Result = {
    import AutoBraceUtils._

    val caretWS = element match {
      case ws: PsiWhiteSpace => ws
      case _ => return Result.CONTINUE
    }

    val caretWSText = caretWS.getText
    val posInWs = caretOffset - element.getTextOffset
    val newlinePosBeforeCaret = caretWSText.lastIndexOf('\n', posInWs - 1)

    if (newlinePosBeforeCaret < 0) {
      // caret is not at some indentation position but rather there is something before us in the same line
      return Result.CONTINUE
    }


    // ========= Get block that should be wraped ==========
    // caret could be before or after the expression that should be wrapped
    val (expr, exprWS, caretIsBeforeExpr) = nextExpressionInIndentationContext(element) match {
      case Some(expr) => (expr, caretWS, true)
      case None =>
        previousExpressionInIndentationContext(element) match {
          case Some(expr) =>
            val exprWs = expr.prevElement match {
              case Some(ws: PsiWhiteSpace) => ws
              case _ => return Result.CONTINUE
            }
            (expr, exprWs, false)
          case None => return Result.CONTINUE
        }
    }
    val exprWSText = exprWS.getText

    // ========= Check correct indention ==========
    val newlinePosBeforeExpr = exprWSText.lastIndexOf('\n')
    if (newlinePosBeforeExpr < 0) {
      return Result.CONTINUE
    }
    val exprIndent = exprWSText.substring(newlinePosBeforeExpr)
    val caretIndent = caretWSText.substring(newlinePosBeforeCaret, posInWs)

    if (exprIndent != caretIndent) {
      return Result.CONTINUE
    }

    // ========= Insert braces ==========
    // Start with the opening brace, then the user input, and then the closing brace
    val document = editor.getDocument

    val openingBraceOffset =
      exprWS
        .prevSiblingNotWhitespaceComment
        .fold(exprWS.startOffset)(_.endOffset)
//    val openingBraceOffset = exprWS.startOffset
    document.insertString(openingBraceOffset, "{")
    val openingBraceRange = TextRange.from(openingBraceOffset, 1)
    val displacementAfterClosingBrace = 1

    val closingBraceRange = if (caretIsBeforeExpr) {
      document.insertString(caretOffset + displacementAfterClosingBrace, c.toString)

      val displacementAfterUserInput = displacementAfterClosingBrace + 1
      editor.getCaretModel.moveToOffset(caretOffset + displacementAfterUserInput)

      val closingBraceOffset = expr.endOffset + displacementAfterUserInput
      document.insertString(closingBraceOffset, "\n}")
      TextRange.from(closingBraceOffset, 3)
    } else {
      // we want the closing brace right after the user input, so put it here
      val inputAndClosingBraceOffset = caretOffset + displacementAfterClosingBrace
      document.insertString(inputAndClosingBraceOffset, c + "\n}")

      val afterInputOffset = inputAndClosingBraceOffset + 1
      editor.getCaretModel.moveToOffset(afterInputOffset)
      TextRange.from(afterInputOffset, 3)
    }
    document.commit(project)

    CodeStyleManager.getInstance(project).reformatText(file, ju.Arrays.asList(openingBraceRange, closingBraceRange))

    Result.STOP
  }

  private def handleLeftBraceWrap(caretOffset: Int, element: PsiElement)
                                 (implicit project: Project, file: PsiFile, editor: Editor, settings: CodeStyleSettings): Result =
    findElementToWrap(element) match {
      case Some(wrapInfo) =>
        if (!wrapInfo.wrapElement.isInstanceOf[ScBlockExpr])
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
      } else {
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
        document.insertString(elementActualEndOffset, "\n}")
        document.commit(project)

        val ranges = ju.Arrays.asList(
          TextRange.from(caretOffset, 1),
          TextRange.from(elementActualEndOffset, 3)
        )
        CodeStyleManager.getInstance(project).reformatText(file, ranges)
      }

      Result.STOP
    } else {
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
    isClosingBrace && PsiTreeUtil.nextLeaf(next).isInstanceOf[PsiErrorElement]
  }
}

object ScalaTypedHandler {
  private val wiki1LTagMatch = Set("^", "`")
  private val wiki2LTagMatch = Map(
    "__" -> "__",
    "''" -> "''",
    ",," -> ",,",
    "[[" -> "]]"
  )

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
      case f: ScFor                     => ((if (f.isYield) f.getYield else f.getRightBracket).contains, f.body, wrap(_))
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
