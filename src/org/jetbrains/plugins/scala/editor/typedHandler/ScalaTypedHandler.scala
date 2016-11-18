package org.jetbrains.plugins.scala.editor.typedHandler

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate.Result
import com.intellij.codeInsight.{AutoPopupController, CodeInsightSettings}
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Condition
import com.intellij.psi.codeStyle.{CodeStyleManager, CodeStyleSettingsManager}
import com.intellij.psi.{PsiDocumentManager, PsiElement, PsiFile, PsiWhiteSpace}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterClause
import org.jetbrains.plugins.scala.{ScalaLanguage, extensions}
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenTypes, ScalaXmlTokenTypes}
import org.jetbrains.plugins.scala.lang.lexer.ScalaXmlTokenTypes.PatchedXmlLexer
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScEnumerators, ScIfStmt, ScReferenceExpression}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml._
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.docsyntax.ScaladocSyntaxElementType
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings


/**
 * @author Alexander Podkhalyuzin
 * @author Dmitry Naydanov
 */
class ScalaTypedHandler extends TypedHandlerDelegate {
  override def charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result = {
    if (!file.isInstanceOf[ScalaFile]) return Result.CONTINUE

    val offset = editor.getCaretModel.getOffset
    val element = file.findElementAt(offset - 1)
    if (element == null) return Result.CONTINUE

    val document = editor.getDocument
    val text = document.getText
    var myTask: (Document, Project, PsiElement, Int) => Unit = null

    def chooseXmlTask(withAttr: Boolean) {
      c match {
        case '>' => myTask = completeXmlTag(tag => "</" + Option(tag.getTagName).getOrElse("") + ">")
        case '/' => myTask = completeEmptyXmlTag(editor)
        case '=' if withAttr => myTask = completeXmlAttributeQuote(editor)
        case _ =>
      }
    }

    if (isInDocComment(element)) {//we don't have to check offset >= 3 because "/**" is already has 3 characters
      myTask = getScaladocTask(text, offset)
    } else if (c == ' ' && offset >= 6 && offset < text.length && text.substring(offset - 6, offset) == " case ") {
      myTask = indentCase(file)
    } else if (c == ' ' && offset >= 6 && offset < text.length && text.substring(offset - 6, offset) == " else ") {
      myTask = indentElse(file)
    } else if (c == '{' && offset >= 2 && offset < text.length && text.substring(offset - 2, offset) == " {") {
      myTask = indentValBraceStyle(file)
    } else if (isInPlace(element, classOf[ScXmlExpr], classOf[ScXmlPattern])) {
      chooseXmlTask(withAttr = true)
    } else if (file.findElementAt(offset - 2) 
        match {case i: PsiElement if !ScalaNamesUtil.isOperatorName(i.getText) && i.getText != "=" =>
        c == '>' || c == '/' ; case _ => false}) {
      chooseXmlTask(withAttr = false)
    } else if (element.getPrevSibling != null && element.getPrevSibling.getNode.getElementType == ScalaElementTypes.CASE_CLAUSES) {
      val ltIndex = element.getPrevSibling.getText.indexOf("<")
      if (ltIndex > "case ".length - 1 && element.getPrevSibling.getText.substring(0, ltIndex).trim() == "case") {
        chooseXmlTask(withAttr = false)
      }
    } else if (c == '{' && (element.getParent match {
            case l: ScInterpolatedStringLiteral => !l.isMultiLineString; case _ => false} )) {
      myTask = completeInterpolatedStringBraces
    } else if (c == '>' || c == '-') {
      myTask = replaceArrowTask(file, editor)
    } else if (c == '$') {
      myTask = startAutopopupCompletion(file, editor)
    } else if (c == '{') {
      myTask = convertToInterpolated(file, editor)
    } else if (c == '.') {
      myTask = startAutopopupCompletionInInterpolatedString(file, editor)
    } else if (offset > 1){
      val prevPositionElement = file.findElementAt(offset - 2)
      if (ScalaPsiUtil.isLineTerminator(prevPositionElement)) {
        prevPositionElement.getPrevSiblingCondition(_.getTextLength != 0).foreach(_.getNode.getElementType match {
          case ScalaTokenTypes.tDOT => myTask = indentRefExprDot(file)
          case ScalaTokenTypes.tCOMMA => myTask = indentParametersComma(file)
          case ScalaTokenTypes.tASSIGN => myTask = indentDefinitionAssign(file)
          case ScalaTokenTypes.tSEMICOLON => myTask = indentForGenerators(file)
          case _ =>
        })
      }
    }

    if (myTask == null) return Result.CONTINUE

    extensions.inWriteAction {
      PsiDocumentManager.getInstance(project).commitDocument(document)
    }

    myTask(document, project, file.findElementAt(offset - 1), offset) // prev element is not valid here
    Result.STOP
  }

  override def beforeCharTyped(c: Char, project: Project, editor: Editor, file: PsiFile, fileType: FileType): Result = {
    if (!file.isInstanceOf[ScalaFile]) return Result.CONTINUE

    val offset = editor.getCaretModel.getOffset
    val element = file.findElementAt(offset)
    val prevElement = file.findElementAt(offset - 1)
    if (element == null) return Result.CONTINUE
    val elementType = element.getNode.getElementType

    val settings = ScalaCodeStyleSettings.getInstance(project)

    def moveCaret() {
      editor.getCaretModel.moveCaretRelatively(1, 0, false, false, false)
    }

    // TODO split "if" condition
    if ((c == '"' && Set(ScalaTokenTypes.tMULTILINE_STRING, ScalaTokenTypes.tINTERPOLATED_STRING_END).contains(elementType) &&
            element.getTextOffset + element.getTextLength - offset < 4) ||
            isInDocComment(element) && (elementType.isInstanceOf[ScaladocSyntaxElementType] ||
                    elementType == ScalaDocTokenType.DOC_INNER_CLOSE_CODE_TAG) &&
                    element.getParent.getLastChild == element && element.getText.startsWith("" + c) &&
                    !(elementType == ScalaDocTokenType.DOC_ITALIC_TAG && element.getPrevSibling != null
                            && element.getPrevSibling.getNode.getElementType == ScalaDocTokenType.DOC_ITALIC_TAG)) {
      moveCaret()
      return Result.STOP
    } else if (c == '"' && elementType == ScalaXmlTokenTypes.XML_ATTRIBUTE_VALUE_END_DELIMITER) {
      moveCaret()
      return Result.STOP
    } else if ((c == '>' || c == '/') && elementType == ScalaXmlTokenTypes.XML_EMPTY_ELEMENT_END) {
      moveCaret()
      return Result.STOP
    } else if (c == '>' && elementType == ScalaXmlTokenTypes.XML_TAG_END) {
      moveCaret()
      return Result.STOP
    } else if (c == '>' && prevElement != null && prevElement.getNode.getElementType ==ScalaXmlTokenTypes.XML_EMPTY_ELEMENT_END) {
      return Result.STOP
    } else if (c == '>' && settings.REPLACE_CASE_ARROW_WITH_UNICODE_CHAR && prevElement != null &&
      prevElement.getNode.getElementType == ScalaTokenTypes.tFUNTYPE) {
      return Result.STOP
    } else if (c == '"' && prevElement != null && ScalaApplicationSettings.getInstance().INSERT_MULTILINE_QUOTES) {
      val prevType = prevElement.getNode.getElementType

      if (elementType != ScalaTokenTypes.tSTRING && prevType == ScalaTokenTypes.tSTRING &&
              prevElement.getParent.getText == "\"\"") {
        completeMultilineString(editor, project, element, offset)
      } else if (prevType == ScalaTokenTypes.tINTERPOLATED_STRING_END && elementType != ScalaTokenTypes.tINTERPOLATED_STRING_END &&
              Set("f\"\"", "s\"\"").contains(prevElement.getParent.getText)) {
        completeMultilineString(editor, project, element, offset)
      }
    } else if (c == '.' && ScalaPsiUtil.isLineTerminator(prevElement)) {
      //hacky way to introduce indent without calling formatter; this is better then turning formatting model into a mess
      val indent = CodeStyleSettingsManager.getSettings(project).
        getCommonSettings(ScalaLanguage.Instance).getIndentOptions.CONTINUATION_INDENT_SIZE
      val indentString = (for (i <- 1 to indent) yield " ").foldLeft("")(_ + _)
      extensions.inWriteAction {
        val document = editor.getDocument
        document.insertString(offset, indentString)
        PsiDocumentManager.getInstance(project).commitDocument(document)
      }
    }

    Result.CONTINUE
  }

  private def isInDocComment(element: PsiElement): Boolean = isInPlace(element, classOf[ScDocComment])

  private def isInPlace(element: PsiElement, place : Class[_ <: PsiElement]*): Boolean = {
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
      case tag: ScXmlStartTag  =>
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

  private def completeMultilineString(editor: Editor, project: Project, element: PsiElement, offset: Int) {
    extensions.inWriteAction {
      val document = editor.getDocument
      document.insertString(offset, "\"\"\"")
      PsiDocumentManager.getInstance(project).commitDocument(document)
    }
  }

  private def insertAndCommit(offset: Int, text: String, document: Document, project: Project) {
    extensions.inWriteAction {
      document.insertString(offset, text)
      PsiDocumentManager.getInstance(project).commitDocument(document)
    }
  }

  private def getScaladocTask(text: String, offset: Int): (Document, Project, PsiElement, Int) => Unit = {
    import org.jetbrains.plugins.scala.editor.typedHandler.ScalaTypedHandler._
    if (offset < 3 || text.length <= offset) return null
    
    text.charAt(offset) match {
      case ' '|'\n'|'\t'|'\r'|''' =>
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
      elem => elem.getNode.getElementType == ScalaTokenTypes.kELSE && elem.getParent.isInstanceOf[ScIfStmt])
  }

  private def indentRefExprDot(file: PsiFile)(document: Document, project: Project, element: PsiElement, offset: Int) = {
    indentElement(file)(document, project, element, offset,
      _ => true,
      elem => elem.getParent.isInstanceOf[ScReferenceExpression])
  }

  private def indentParametersComma(file: PsiFile)(document: Document, project: Project, element: PsiElement, offset: Int) = {
    indentElement(file)(document, project, element, offset, _ => true,
      ScalaPsiUtil.getParent(_, 2).exists {
        case _: ScParameterClause | _: ScArgumentExprList => true
        case _ => false
      })
  }

  private def indentDefinitionAssign(file: PsiFile)(document: Document, project: Project, element: PsiElement, offset: Int) = {
    indentElement(file)(document, project, element, offset, _ => true,
      ScalaPsiUtil.getParent(_, 2).exists {
        case _: ScFunction | _: ScVariable | _: ScValue | _: ScTypeAlias => true
        case _ => false
      })
  }

  private def indentForGenerators(file: PsiFile)(document: Document, project: Project, element: PsiElement, offset: Int) = {
    indentElement(file)(document, project, element, offset, ScalaPsiUtil.isLineTerminator,
      ScalaPsiUtil.getParent(_, 3).exists{_.isInstanceOf[ScEnumerators]})
  }

  private def indentValBraceStyle(file: PsiFile)(document: Document, project: Project, element: PsiElement, offset: Int) = {
    indentElement(file)(document, project, element, offset, ScalaPsiUtil.isLineTerminator,
      ScalaPsiUtil.getParent(_, 2).exists(_.isInstanceOf[ScValue]))
  }

  private def indentElement(file: PsiFile)(document: Document, project: Project, element: PsiElement, offset: Int,
                                           prevCondition: PsiElement => Boolean,
                                           condition: PsiElement => Boolean = element => element.isInstanceOf[PsiWhiteSpace] || ScalaPsiUtil.isLineTerminator(element)) {
    if (condition(element)) {
      val anotherElement = file.findElementAt(offset - 2)
      if (prevCondition(anotherElement)) {
        extensions.inWriteAction {
          PsiDocumentManager.getInstance(project).commitDocument(document)
          CodeStyleManager.getInstance(project).adjustLineIndent(file, anotherElement.getTextRange)
        }
      }
    }
  }

  private def replaceArrowTask(file: PsiFile, editor: Editor)(document: Document, project: Project, element: PsiElement, offset: Int) {
    @inline def replaceElement(replaceWith: String) {
      extensions.inWriteAction {
        document.replaceString(element.getTextRange.getStartOffset, element.getTextRange.getEndOffset, replaceWith)
        PsiDocumentManager.getInstance(project).commitDocument(document)
      }
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

  private def startAutopopupCompletion(file: PsiFile, editor: Editor)(document: Document, project: Project, element: PsiElement, offset: Int) {
    if (CodeInsightSettings.getInstance().AUTO_POPUP_COMPLETION_LOOKUP) {
      element.getParent match {
        case l: ScLiteral =>
          element.getNode.getElementType match {
            case ScalaTokenTypes.tSTRING | ScalaTokenTypes.tMULTILINE_STRING =>
              if (l.getText.filter(_ == '$').length == 1) scheduleAutopopup(file, editor, project)
            case _ =>
          }
        case _ =>
      }
    }
  }

  private def scheduleAutopopup(file: PsiFile, editor: Editor, project: Project): Unit = {
    AutoPopupController.getInstance(project).scheduleAutoPopup(
      editor, CompletionType.BASIC, new Condition[PsiFile] {
        def value(t: PsiFile): Boolean = t == file
      }
    )
  }

  private def startAutopopupCompletionInInterpolatedString(file: PsiFile, editor: Editor)
                                                          (document: Document, project: Project, element: PsiElement, offset: Int) {
    if (CodeInsightSettings.getInstance().AUTO_POPUP_COMPLETION_LOOKUP) {
      element.getParent match {
        case _: ScLiteral =>
          element.getNode.getElementType match {
            case ScalaTokenTypes.tINTERPOLATED_STRING | ScalaTokenTypes.tINTERPOLATED_MULTILINE_STRING =>
              file.findElementAt(offset).getPrevSibling match {
                case _: ScReferenceExpression => scheduleAutopopup(file, editor, project)
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
              if (l.getText.filter(_ == '$').length == 1 && file.getText.charAt(offset - 2) == '$') {
                extensions.inWriteAction {
                  if (file.getText.charAt(offset) != '}' && CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) {
                    document.insertString(offset, "}")
                  }
                  document.insertString(l.getTextRange.getStartOffset, "s")
                  PsiDocumentManager.getInstance(project).commitDocument(document)
                }
              }
            case _ =>
          }
        case _ =>
      }
    }
  }

  private def completeEmptyXmlTag(editor: Editor)(document: Document, project: Project, element: PsiElement, offset: Int) {
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

      extensions.inWriteAction {
        document.insertString(offset, ">")
        editor.getCaretModel.moveCaretRelatively(1, 0, false, false, false)
        PsiDocumentManager.getInstance(project).commitDocument(document)
      }
    }
  }
}

object ScalaTypedHandler {
  val wiki1LTagMatch = Set("^", "`")
  val wiki2LTagMatch = Map("__" -> "__", "''" -> "''", ",," -> ",,", "[[" -> "]]")

  val unicodeCaseArrow = "⇒"
  val unicodeMapArrow = "→"
  val unicodeForGeneratorArrow = "←"
}
