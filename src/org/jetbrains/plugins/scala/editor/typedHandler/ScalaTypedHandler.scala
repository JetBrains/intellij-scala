package org.jetbrains.plugins.scala.editor.typedHandler

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate.Result
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.openapi.editor.{Document, Editor}
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.docsyntax.ScaladocSyntaxElementType
import com.intellij.psi.{PsiElement, PsiDocumentManager, PsiWhiteSpace, PsiFile}
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment
import com.intellij.openapi.fileTypes.FileType
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.extensions
import com.intellij.psi.xml.XmlTokenType
import com.intellij.lexer.XmlLexer
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml._
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolatedStringLiteral
import dk.brics.automaton.BasicOperations


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
        case '>' => myTask = completeXmlTag(tag => "</" + Option(tag.getTagName).getOrElse("") + ">") _
        case '/' => myTask = completeEmptyXmlTag(editor) _
        case '=' if withAttr => myTask = completeXmlAttributeQuote(editor) _
        case _ =>
      }
    }

    if (isInDocComment(element)) {//we don't have to check offset >= 3 because "/**" is already has 3 characters
      myTask = getScaladocTask(text, offset)
    } else if (c == ' ' && offset >= 6 && offset < text.length && text.substring(offset - 6, offset) == " case ") {
      myTask = indentCase(file)
    } else if (isInPlace(element, classOf[ScXmlExpr], classOf[ScXmlPattern])) {
      chooseXmlTask(true)
    } else if (file.findElementAt(offset - 2) 
        match {case i: PsiElement if !ScalaNamesUtil.isOperatorName(i.getText) => c == '>' || c == '/' case _ => false}) {
      chooseXmlTask(false)
    } else if (element.getPrevSibling != null && element.getPrevSibling.getNode.getElementType == ScalaElementTypes.CASE_CLAUSES) {
      val ltIndex = element.getPrevSibling.getText.indexOf("<")
      if (ltIndex > "case ".length - 1 && element.getPrevSibling.getText.substring(0, ltIndex).trim() == "case") {
        chooseXmlTask(false)
      }
    } else if (c == '{' && (element.getParent match {
            case l: ScInterpolatedStringLiteral => !l.isMultiLineString; case _ => false} )) {
      myTask = completeInterpolatedStringBraces 
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
    } else if (c == '"' && element.getNode.getElementType == XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER) {
      moveCaret()
      return Result.STOP
    } else if ((c == '>' || c == '/') && element.getNode.getElementType == XmlTokenType.XML_EMPTY_ELEMENT_END) {
      moveCaret()
      return Result.STOP
    } else if (c == '>' && element.getNode.getElementType == XmlTokenType.XML_TAG_END) {
      moveCaret()
      return Result.STOP
    } else if (c == '>' && prevElement != null && prevElement.getNode.getElementType == XmlTokenType.XML_EMPTY_ELEMENT_END) {
      return Result.STOP
    } else if (c == '"' && prevElement != null) {
      val prevType = prevElement.getNode.getElementType

      if (elementType != ScalaTokenTypes.tSTRING && prevType == ScalaTokenTypes.tSTRING &&
              prevElement.getParent.getText == "\"\"") {
        completeMultilineString(editor, project, element, offset)
      } else if (prevType == ScalaTokenTypes.tINTERPOLATED_STRING_END && elementType != ScalaTokenTypes.tINTERPOLATED_STRING_END &&
              Set("f\"\"", "s\"\"").contains(prevElement.getParent.getText)) {
        completeMultilineString(editor, project, element, offset)
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
      if (Option(tag.getClosingTag).map(_.getTagName != tag.getTagName).getOrElse(true)) 
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
    import ScalaTokenTypes._
    
    if (element.getNode.getElementType == tLBRACE && 
      Option(element.getParent.getPrevSibling).map(_.getNode.getElementType == tINTERPOLATED_STRING_INJECTION).getOrElse(false)) {
      insertAndCommit(offset, "}", document, project)
    }
  }

  private def completeXmlAttributeQuote(editor: Editor)(document: Document, project: Project, element: PsiElement, offset: Int) {
    if (element != null && element.getNode.getElementType == XmlTokenType.XML_EQ && element.getParent != null &&
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
    import ScalaTypedHandler._
    if (offset < 3 || text.length < offset) {
      return null
    }

    if (text.substring(offset - 3, offset) == "'''") {
      completeScalaDocBoldSyntaxElement
    } else if (wiki1LTagMatch.contains(text.substring(offset - 1, offset))) {
      completeScalaDocWikiSyntax(text.substring(offset - 1, offset))
    } else if (wiki2LTagMatch.contains(text.substring(offset - 2, offset))) {
      completeScalaDocWikiSyntax(wiki2LTagMatch.get(text.substring(offset - 2, offset)).get)
    } else if (text.substring(offset - 3, offset) == "{{{") {
      completeScalaDocWikiSyntax("}}}")
    } else {
      null
    }
  }

  private def indentCase(file: PsiFile)(document: Document, project: Project, element: PsiElement, offset: Int) {
    if (element.isInstanceOf[PsiWhiteSpace] || ScalaPsiUtil.isLineTerminator(element)) {
      val anotherElement = file.findElementAt(offset - 2)
      if (anotherElement.getNode.getElementType == ScalaTokenTypes.kCASE &&
              anotherElement.getParent.isInstanceOf[ScCaseClause]) {
        extensions.inWriteAction {
          PsiDocumentManager.getInstance(project).commitDocument(document)
          CodeStyleManager.getInstance(project).adjustLineIndent(file, anotherElement.getTextRange)
        }
      }
    }
  }

  private def completeEmptyXmlTag(editor: Editor)(document: Document, project: Project, element: PsiElement, offset: Int) {
    if (element != null && element.getNode.getElementType == XmlTokenType.XML_DATA_CHARACTERS && element.getText == "/" &&
            element.getPrevSibling != null && element.getPrevSibling.isInstanceOf[ScXmlStartTag]) {
      val xmlLexer = new XmlLexer()
      xmlLexer.start(element.getPrevSibling.getText + "/>")
      xmlLexer.advance()

      if (xmlLexer.getTokenType != XmlTokenType.XML_START_TAG_START) return

      while (xmlLexer.getTokenEnd < xmlLexer.getBufferEnd) {
        xmlLexer.advance()
      }

      if (xmlLexer.getTokenType != XmlTokenType.XML_EMPTY_ELEMENT_END) return

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
}