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
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi._
import com.intellij.psi.codeStyle.{CodeStyleManager, CodeStyleSettings}
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.editor.{DocumentExt, EditorExt}
import org.jetbrains.plugins.scala.extensions.{CharSeqExt, PsiFileExt, _}
import org.jetbrains.plugins.scala.highlighter.ScalaCommenter
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaXmlTokenTypes.PatchedXmlLexer
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenTypes, ScalaXmlTokenTypes}
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
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.docsyntax.ScaladocSyntaxElementType
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.util.IndentUtil
import org.jetbrains.plugins.scala.{ScalaFileType, ScalaLanguage}

import scala.collection.JavaConverters._
import org.jetbrains.plugins.scala.lang.scaladoc.ScalaIsCommentComplete


/**
 * @author Alexander Podkhalyuzin
 * @author Dmitry Naydanov
 */
class ScalaTypedHandler extends TypedHandlerDelegate {

  override def charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result = {
    if (!file.isInstanceOf[ScalaFile]) return Result.CONTINUE

    val offset = editor.offset
    val element = file.findElementAt(offset - 1)
    if (element == null) return Result.CONTINUE

    val document = editor.getDocument
    val text = document.getImmutableCharSequence

    type Task = (Document, Project, PsiElement, Int) => Unit

    def chooseXmlTask(withAttr: Boolean): Task = {
      c match {
        case '>' => completeXmlTag(tag => "</" + Option(tag.getTagName).getOrElse("") + ">")
        case '/' => completeEmptyXmlTag(editor)
        case '=' if withAttr => completeXmlAttributeQuote(editor)
        case _ => null
      }
    }

    // TODO: we can avoid allocations by comparing strings inplace, without substring
    @inline
    def hasPrefix(prefix: String): Boolean =
      prefix.length <= offset && offset < text.length &&
        text.substring(offset - prefix.length, offset) == " case "

    val myTask: Task = if (isInDocComment(element)) { //we don't have to check offset >= 3 because "/**" is already has 3 characters
      getScaladocTask(text, offset)
    } else if (c == ' ' && hasPrefix(" case ")) {
      indentCase(file)
    } else if (c == ' ' && hasPrefix(" else ")) {
      indentElse(file)
    } else if (c == '{' && hasPrefix(" {")) {
      indentValBraceStyle(file)
    } else if (isInPlace(element, classOf[ScXmlExpr], classOf[ScXmlPattern])) {
      chooseXmlTask(withAttr = true)
    } else if (file.findElementAt(offset - 2) match {
      case el: PsiElement if !ScalaNamesUtil.isOperatorName(el.getText) && el.getText != "=" =>
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
      replaceArrowTask(file, editor)
    } else if (c == '$') {
      startAutoPopupCompletion(file, editor)
    } else if (c == '{') {
      convertToInterpolated(file, editor)
    } else if (c == '.' && isSingleCharOnLine(editor)) {
      addContinuationIndent
    } else if (c == '.') {
      startAutoPopupCompletionInInterpolatedString(file, editor)
    } else if (offset > 1) {
      val prevPositionElement = file.findElementAt(offset - 2)
      if (ScalaPsiUtil.isLineTerminator(prevPositionElement)) {
        val prevSibling = ScalaTypedHandler.getPrevSiblingCondition(prevPositionElement)
        prevSibling.map(_.getNode.getElementType).orNull match {
          case ScalaTokenTypes.tDOT => indentRefExprDot(file)
          case ScalaTokenTypes.tCOMMA => indentParametersComma(file)
          case ScalaTokenTypes.tASSIGN => indentDefinitionAssign(file)
          case ScalaTokenTypes.tSEMICOLON => indentForGenerators(file)
          case _ => null
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

    val offset = editor.offset
    val element = file.findElementAt(offset)
    val prevElement = file.findElementAt(offset - 1)
    if (element == null) return Result.CONTINUE
    val elementType = element.getNode.getElementType

    val settings = CodeStyle.getSettings(project)
    val scalaSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])

    def moveCaret(): Unit = {
      editor.getCaretModel.moveCaretRelatively(1, 0, false, false, false)
    }

    if (c == '"' && isClosingScalaString(offset, element, elementType)) {
      moveCaret()
      Result.STOP
    } else if (c == ' ' && needClosingScaladocTag(element, prevElement, editor)) {
      insertClosingScaladocTag(offset, element, editor)
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
    } else if (c == '"' && prevElement != null && ScalaApplicationSettings.getInstance().INSERT_MULTILINE_QUOTES) {
      val prevType = prevElement.getNode.getElementType

      if (elementType != ScalaTokenTypes.tSTRING && prevType == ScalaTokenTypes.tSTRING &&
        prevElement.getParent.getText == "\"\"") {
        completeMultilineString(offset, editor, project)
      } else if (prevType == ScalaTokenTypes.tINTERPOLATED_STRING_END && elementType != ScalaTokenTypes.tINTERPOLATED_STRING_END &&
        Set("f\"\"", "s\"\"", "q\"\"").contains(prevElement.getParent.getText)) {
        completeMultilineString(offset, editor, project)
      }
      Result.CONTINUE
    } else if (c == '{' && CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) {
      handleLeftBrace(offset, element, project, file, editor, settings)
    } else {
      Result.CONTINUE
    }
  }

  @inline
  private def isClosingScalaString(offset: Int, element: PsiElement, elementType: IElementType): Boolean = {
    Set(ScalaTokenTypes.tMULTILINE_STRING, ScalaTokenTypes.tINTERPOLATED_STRING_END).contains(elementType) &&
      element.getTextOffset + element.getTextLength - offset <= 3
  }

  @inline
  private def isClosingScaladocTagOrMarkup(c: Char, element: PsiElement, elementType: IElementType) = {
    (elementType.isInstanceOf[ScaladocSyntaxElementType] || elementType == ScalaDocTokenType.DOC_INNER_CLOSE_CODE_TAG) &&
      isInDocComment(element) &&
      element.getParent.getLastChild == element && element.getText.startsWith("" + c) &&
      // handling case when '`' was type right after second '`' inside "````" to enable bold syntax ("```bold text```)"
      !(elementType == ScalaDocTokenType.DOC_ITALIC_TAG &&
        element.getPrevSibling.nullSafe.map(_.getNode.getElementType).get == ScalaDocTokenType.DOC_ITALIC_TAG)
  }

  @inline
  private def isInDocComment(element: PsiElement): Boolean = isInPlace(element, classOf[ScDocComment])

  private def needClosingScaladocTag(element: PsiElement, prevElement: PsiElement, editor: Editor): Boolean = {
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
  }

  private def insertClosingScaladocTag(offset: Int, element: PsiElement, editor: Editor): Unit = {
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

  private def completeScalaDocWikiSyntax(tagToInsert: String)(document: Document, project: Project, element: PsiElement, offset: Int) {
    if (element.getNode.getElementType.isInstanceOf[ScaladocSyntaxElementType] || tagToInsert == "}}}") {
      insertAndCommit(offset, tagToInsert, document, project)
    }
  }

  private def completeScalaDocBoldSyntaxElement(document: Document, project: Project, element: PsiElement, offset: Int) {
    if (element.getNode.getElementType.isInstanceOf[ScaladocSyntaxElementType]) {
      insertAndCommit(offset, "'", document, project)
    }
  }

  private def completeXmlTag(insert: ScXmlStartTag => String)(document: Document, project: Project, element: PsiElement, offset: Int) {
    if (element == null) return

    def doInsert(tag: ScXmlStartTag) {
      insertAndCommit(offset, insert(tag), document, project)
    }

    def check(tag: ScXmlStartTag) {
      if (Option(tag.getClosingTag).forall(_.getTagName != tag.getTagName))
        doInsert(tag)
    }

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

  private def completeInterpolatedStringBraces(document: Document, project: Project, element: PsiElement, offset: Int) {
    if (element == null) return
    import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._

    if (element.getNode.getElementType == tLBRACE &&
      Option(element.getParent.getPrevSibling).exists(_.getNode.getElementType == tINTERPOLATED_STRING_INJECTION) &&
      (element.getNextSibling == null || element.getNextSibling.getNode.getElementType != tRBRACE)) {
      insertAndCommit(offset, "}", document, project)
    }
  }

  private def completeXmlAttributeQuote(editor: Editor)(document: Document, project: Project, element: PsiElement, offset: Int) {
    if (element != null && element.getNode.getElementType == ScalaXmlTokenTypes.XML_EQ && element.getParent != null &&
      element.getParent.isInstanceOf[ScXmlAttribute]) {
      insertAndCommit(offset, "\"\"", document, project)
      editor.getCaretModel.moveCaretRelatively(1, 0, false, false, false)
    }
  }

  private def completeMultilineString(offset: Int, editor: Editor, project: Project) {
    val document = editor.getDocument
    insertAndCommit(offset, "\"\"\"", document, project)
  }

  private def insertAndCommit(offset: Int, text: String, document: Document, project: Project) {
    document.insertString(offset, text)
    document.commit(project)
  }

  private def getScaladocTask(text: CharSequence, offset: Int): (Document, Project, PsiElement, Int) => Unit = {
    import org.jetbrains.plugins.scala.editor.typedHandler.ScalaTypedHandler._
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

  private def indentCase(file: PsiFile)(document: Document, project: Project, element: PsiElement, offset: Int) {
    indentElement(file)(document, project, element, offset,
      elem => elem.getNode.getElementType == ScalaTokenTypes.kCASE && elem.getParent.isInstanceOf[ScCaseClause])
  }

  private def indentElse(file: PsiFile)(document: Document, project: Project, element: PsiElement, offset: Int) {
    indentElement(file)(document, project, element, offset,
      elem => elem.getNode.getElementType == ScalaTokenTypes.kELSE && elem.getParent.isInstanceOf[ScIf])
  }

  private def indentRefExprDot(file: PsiFile)(document: Document, project: Project, element: PsiElement, offset: Int): Unit = {
    indentElement(file)(document, project, element, offset,
      _ => true,
      elem => elem.getParent.isInstanceOf[ScReferenceExpression])
  }

  private def indentParametersComma(file: PsiFile)(document: Document, project: Project, element: PsiElement, offset: Int): Unit = {
    indentElement(file)(document, project, element, offset, _ => true,
      ScalaPsiUtil.getParent(_, 2).exists {
        case _: ScParameterClause | _: ScArgumentExprList => true
        case _ => false
      })
  }

  private def indentDefinitionAssign(file: PsiFile)(document: Document, project: Project, element: PsiElement, offset: Int): Unit = {
    indentElement(file)(document, project, element, offset, _ => true,
      ScalaPsiUtil.getParent(_, 2).exists {
        case _: ScFunction | _: ScVariable | _: ScValue | _: ScTypeAlias => true
        case _ => false
      })
  }

  private def indentForGenerators(file: PsiFile)(document: Document, project: Project, element: PsiElement, offset: Int): Unit = {
    indentElement(file)(document, project, element, offset, ScalaPsiUtil.isLineTerminator,
      ScalaPsiUtil.getParent(_, 3).exists(_.isInstanceOf[ScEnumerators]))
  }

  private def indentValBraceStyle(file: PsiFile)(document: Document, project: Project, element: PsiElement, offset: Int): Unit = {
    indentElement(file)(document, project, element, offset, ScalaPsiUtil.isLineTerminator,
      ScalaPsiUtil.getParent(_, 2).exists(_.isInstanceOf[ScValue]))
  }

  private def indentElement(file: PsiFile)(document: Document, project: Project, element: PsiElement, offset: Int,
                                           prevCondition: PsiElement => Boolean,
                                           condition: PsiElement => Boolean = element => element.isInstanceOf[PsiWhiteSpace] || ScalaPsiUtil.isLineTerminator(element)) {
    if (condition(element)) {
      val anotherElement = file.findElementAt(offset - 2)
      if (prevCondition(anotherElement)) {
        document.commit(project)
        CodeStyleManager.getInstance(project).adjustLineIndent(file, anotherElement.getTextRange)
      }
    }
  }

  private def addContinuationIndent(document: Document, project: Project, element: PsiElement, offset: Int): Unit = {
    val file = element.getContainingFile
    val elementOffset = element.getTextOffset
    val lineStart = document.lineStartOffset(offset)

    CodeStyleManager.getInstance(project).adjustLineIndent(file, elementOffset)

    val additionalIndentSize =
      CodeStyle.getLanguageSettings(file, ScalaLanguage.INSTANCE).getIndentOptions.CONTINUATION_INDENT_SIZE
    val indentString = StringUtil.repeatSymbol(' ', additionalIndentSize)
    document.insertString(lineStart, indentString)
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

  private def replaceArrowTask(file: PsiFile, editor: Editor)(document: Document, project: Project, element: PsiElement, offset: Int) {
    @inline def replaceElement(replaceWith: String) {
      document.replaceString(element.getTextRange.getStartOffset, element.getTextRange.getEndOffset, replaceWith)
      document.commit(project)
    }

    val settings = ScalaCodeStyleSettings.getInstance(project)

    element.getNode.getElementType match {
      case ScalaTokenTypes.tFUNTYPE if settings.REPLACE_CASE_ARROW_WITH_UNICODE_CHAR =>
        replaceElement(ScalaTypedHandler.unicodeCaseArrow)
      case ScalaTokenTypes.tIDENTIFIER if settings.REPLACE_MAP_ARROW_WITH_UNICODE_CHAR && element.getText == "->" =>
        replaceElement(ScalaTypedHandler.unicodeMapArrow)
      case ScalaTokenTypes.tCHOOSE if settings.REPLACE_FOR_GENERATOR_ARROW_WITH_UNICODE_CHAR =>
        replaceElement(ScalaTypedHandler.unicodeForGeneratorArrow)
      case _ =>
    }
  }

  private def startAutoPopupCompletion(file: PsiFile, editor: Editor)(document: Document, project: Project, element: PsiElement, offset: Int): Unit = {
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
  }

  private def scheduleAutoPopup(file: PsiFile, editor: Editor, project: Project): Unit = {
    AutoPopupController.getInstance(project).scheduleAutoPopup(
      editor, CompletionType.BASIC, (t: PsiFile) => t == file
    )
  }

  private def startAutoPopupCompletionInInterpolatedString(file: PsiFile, editor: Editor)
                                                          (document: Document, project: Project, element: PsiElement, offset: Int) {
    if (CodeInsightSettings.getInstance().AUTO_POPUP_COMPLETION_LOOKUP) {
      element.getParent match {
        case _: ScLiteral =>
          element.getNode.getElementType match {
            case ScalaTokenTypes.tINTERPOLATED_STRING | ScalaTokenTypes.tINTERPOLATED_MULTILINE_STRING =>
              file.findElementAt(offset).getPrevSibling match {
                case _: ScReferenceExpression =>
                  scheduleAutoPopup(file, editor, project)
                case _ =>
              }
            case _ =>
          }
        case _ =>
      }
    }
  }

  private def convertToInterpolated(file: PsiFile, editor: Editor)(document: Document, project: Project, element: PsiElement, offset: Int) {
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
                document.insertString(l.getTextRange.getStartOffset, "s")
                document.commit(project)
              }
            case _ =>
          }
        case _ =>
      }
    }
  }

  private def completeEmptyXmlTag(editor: Editor)(document: Document, project: Project, element: PsiElement, offset: Int): Unit = {
    if (element != null && element.getNode.getElementType == ScalaXmlTokenTypes.XML_DATA_CHARACTERS && element.getText == "/" &&
      element.getPrevSibling != null && element.getPrevSibling.isInstanceOf[ScXmlStartTag]) {
      val xmlLexer = new PatchedXmlLexer
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
  }

  private def handleLeftBrace(offset: Int, element: PsiElement, project: Project, file: PsiFile, editor: Editor, settings: CodeStyleSettings): Result = {
    val assignElement: PsiElement = {
      PsiTreeUtil.prevLeaf(element) match {
        case ws: PsiWhiteSpace => PsiTreeUtil.prevLeaf(ws)
        case prev => prev
      }
    }

    if (assignElement != null && assignElement.getNode.getElementType == ScalaTokenTypes.tASSIGN) {
      val definition = assignElement.getParent
      val bodyOpt: Option[ScExpression] = definition match {
        case patDef: ScPatternDefinition if patDef.bindings.size == 1 => patDef.expr
        case varDef: ScVariableDefinition => varDef.expr
        case funDef: ScFunctionDefinition => funDef.body
        case _ => None
      }

      bodyOpt match {
        case Some(_: ScBlockExpr) =>
          Result.CONTINUE
        case Some(body) =>
          wrapDefinitionWithBraces(offset, definition, assignElement, body, project, file, editor, settings)
        case _ =>
          Result.CONTINUE
      }
    } else {
      Result.CONTINUE
    }
  }

  private def wrapDefinitionWithBraces(offset: Int, definition: PsiElement, assignElement: PsiElement, body: PsiElement,
                                       project: Project, file: PsiFile, editor: Editor, settings: CodeStyleSettings): Result = {
    val document = editor.getDocument

    val caretLine = document.getLineNumber(offset)
    val assignLine = document.getLineNumber(assignElement.getTextRange.getStartOffset)
    val bodyStartLine = document.getLineNumber(body.getTextRange.getStartOffset)

    val caretIsBeforeBody = offset <= body.getTextRange.getStartOffset
    val caretAndAssignOnSameLine = caretLine == assignLine
    val singleLineDefinition = assignLine == bodyStartLine

    if (caretIsBeforeBody && caretAndAssignOnSameLine && (singleLineDefinition || isElementIndented(definition, body, settings))) {
      document.insertString(offset, "{")

      // if left brace is inserted on the same line with body we expect the user to press Enter after that
      // in this case we rely that EnterAfterUnmatchedBraceHandler will insert missing closing brace
      val caretAndBodyOnSameLine = caretLine == bodyStartLine
      if (caretAndBodyOnSameLine) {
        editor.getCaretModel.moveToOffset(offset + 1)
      } else {
        val bodyEndLine = document.getLineNumber(body.getTextRange.getEndOffset)
        val bodyTextRange = new TextRange(body.getTextRange.getStartOffset + 1, document.getLineEndOffset(bodyEndLine))
        editor.getCaretModel.moveToOffset(bodyTextRange.getStartOffset)

        val closingBraceOffset = bodyTextRange.getEndOffset
        document.insertString(closingBraceOffset, "\n}")
        document.commit(project)

        CodeStyleManager.getInstance(project).reformatTextWithContext(file, Seq(
          new TextRange(offset, offset + 1),
          new TextRange(closingBraceOffset, closingBraceOffset + 1)
        ).asJava)
      }

      Result.STOP
    } else {
      Result.CONTINUE
    }
  }

  private def isElementIndented(parent: PsiElement, child: PsiElement, settings: CodeStyleSettings): Boolean = {
    val tabSize = settings.getTabSize(ScalaFileType.INSTANCE)
    IndentUtil.compare(child, parent, tabSize) > 0
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
}
