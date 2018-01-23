package org.jetbrains.plugins.scala.conversion

import java.awt.datatransfer.DataFlavor

import com.intellij.codeInsight.editorActions.TextBlockTransferableData
import com.intellij.codeInspection.{InspectionManager, LocalQuickFixOnPsiElement, ProblemDescriptor, ProblemsHolder}
import com.intellij.openapi.editor.{Editor, RangeMarker}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import org.jetbrains.plugins.scala.codeInsight.intention.RemoveBracesIntention
import org.jetbrains.plugins.scala.codeInspection.parentheses.ScalaUnnecessaryParenthesesInspection
import org.jetbrains.plugins.scala.codeInspection.prefixMutableCollections.ReferenceMustBePrefixedInspection
import org.jetbrains.plugins.scala.codeInspection.syntacticSimplification.{RemoveRedundantReturnInspection, ScalaUnnecessarySemicolonInspection}
import org.jetbrains.plugins.scala.conversion.ast.CommentsCollector
import org.jetbrains.plugins.scala.conversion.copy.{Association, ScalaPasteFromJavaDialog}
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, PsiFileExt}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScParenthesisedExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportSelector
import org.jetbrains.plugins.scala.util.TypeAnnotationUtil
import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
  * Created by Kate Ustyuzhanina
  * on 12/8/15
  */
object ConverterUtil {
  def getTopElements(file: PsiFile, startOffsets: Array[Int], endOffsets: Array[Int]): (Seq[Part], mutable.HashSet[PsiElement]) = {

    def buildTextPart(offset1: Int, offset2: Int, dropElements: mutable.HashSet[PsiElement]): TextPart = {
      val possibleComment = file.findElementAt(offset1)
      if (possibleComment != null && CommentsCollector.isComment(possibleComment)) dropElements += possibleComment
      TextPart(new TextRange(offset1, offset2).substring(file.getText))
    }

    val dropElements = new mutable.HashSet[PsiElement]()
    val buffer = new ArrayBuffer[Part]
    for ((startOffset, endOffset) <- startOffsets.zip(endOffsets)) {
      @tailrec
      def findElem(offset: Int): PsiElement = {
        if (offset > endOffset) return null
        val elem = file.findElementAt(offset)
        if (elem == null) return null

        if (elem.getParent.getTextRange.getEndOffset > endOffset ||
          elem.getParent.getTextRange.getStartOffset < startOffset) {
          if (CommentsCollector.isComment(elem) && !dropElements.contains(elem)) {
            buffer += TextPart(elem.getText + "\n")
            dropElements += elem
          }
          findElem(elem.getTextRange.getEndOffset + 1)
        }
        else
          elem
      }

      var elem: PsiElement = findElem(startOffset)
      if (elem != null) {
        while (elem.getParent != null && !elem.getParent.isInstanceOf[PsiFile] &&
          elem.getParent.getTextRange.getEndOffset <= endOffset &&
          elem.getParent.getTextRange.getStartOffset >= startOffset) {
          elem = elem.getParent
        }
        //get wrong result when copy element that has PsiComment without comment
        val shifted = shiftedElement(elem, dropElements, endOffset)
        if (shifted != elem) {
          elem = shifted
        } else if (startOffset < elem.getTextRange.getStartOffset) {
          buffer += buildTextPart(startOffset, elem.getTextRange.getStartOffset, dropElements)
        }

        buffer += ElementPart(elem)
        while (elem.getNextSibling != null && elem.getNextSibling.getTextRange.getEndOffset <= endOffset) {
          elem = elem.getNextSibling
          buffer += ElementPart(elem)
        }

        if (elem.getTextRange.getEndOffset < endOffset) {
          buffer += buildTextPart(elem.getTextRange.getEndOffset, endOffset, dropElements)
        }
      }
    }
    (buffer, dropElements)
  }

  def canDropElement(element: PsiElement): Boolean = {
    element match {
      case _: PsiWhiteSpace => true
      case _: PsiComment => true
      case _: PsiModifierList => true
      case _: PsiAnnotation => true
      case t: PsiJavaToken =>
        val drop = Seq(JavaTokenType.PUBLIC_KEYWORD, JavaTokenType.PROTECTED_KEYWORD, JavaTokenType.PRIVATE_KEYWORD,
          JavaTokenType.STATIC_KEYWORD, JavaTokenType.ABSTRACT_KEYWORD, JavaTokenType.FINAL_KEYWORD,
          JavaTokenType.NATIVE_KEYWORD, JavaTokenType.SYNCHRONIZED_KEYWORD, JavaTokenType.STRICTFP_KEYWORD,
          JavaTokenType.TRANSIENT_KEYWORD, JavaTokenType.VOLATILE_KEYWORD, JavaTokenType.DEFAULT_KEYWORD)

        drop.contains(t.getTokenType) && t.getParent.isInstanceOf[PsiModifierList] ||
          t.getTokenType == JavaTokenType.SEMICOLON
      case c: PsiCodeBlock if c.getParent.isInstanceOf[PsiMethod] => true
      case o => o.getFirstChild == null
    }
  }

  def shiftedElement(inElem: PsiElement, dropElements: mutable.HashSet[PsiElement], endOffset: Int): PsiElement = {
    var elem = inElem
    while (elem.getPrevSibling != null &&
      !elem.getPrevSibling.isInstanceOf[PsiFile] &&
      canDropElement(elem.getPrevSibling)) {

      elem = elem.getPrevSibling
      dropElements += elem
    }

    if (elem.getParent != null && !elem.getParent.isInstanceOf[PsiFile] && elem.getParent.getFirstChild == elem
      && elem.getParent.getTextRange.getEndOffset <= endOffset) {
      elem = elem.getParent
    }
    elem
  }

  //collect top elements in range
  def collectTopElements(startOffset: Int, endOffset: Int, javaFile: PsiFile): Array[PsiElement] = {
    def parentIsValid(psiElement: PsiElement): Boolean = {
      psiElement.parent.exists { p =>
        !p.isInstanceOf[PsiFile] && Option(p.getTextRange).exists(_.getStartOffset == startOffset)
      }
    }

    val parentAtOffset =
      Option(javaFile.findElementAt(startOffset))
        .map(e => Iterator(e) ++ e.parentsInFile)
        .getOrElse(Iterator.empty)
        .dropWhile(parentIsValid)

    if (parentAtOffset.isEmpty) {
      Array.empty[PsiElement]
    } else {
      val elem = parentAtOffset.next()
      val topElements =
        elem.nextSibilingsWithSelf
          .takeWhile(elem => elem != null && elem.getTextRange.getEndOffset < endOffset)
          .map(_.getNextSibling)
          .filter(el => el != null && el.isValid)
          .toArray

      elem +: topElements
    }
  }

  def performePaste(editor: Editor, bounds: RangeMarker, text: String, project: Project): Unit = {
    ConverterUtil.replaceByConvertedCode(editor, bounds, text)
    editor.getCaretModel.moveToOffset(bounds.getStartOffset + text.length)
    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
  }

  /*
    Run under write action
    Remove type annotations & apply inspections
  */
  def cleanCode(file: PsiFile, project: Project, offset: Int, endOffset: Int, editor: Editor = null): Unit = {
    runInspections(file, project, offset, endOffset, editor)

    TypeAnnotationUtil.removeAllTypeAnnotationsIfNeeded(
      ConverterUtil.collectTopElements(offset, endOffset, file)
    )
  }

  def runInspections(file: PsiFile, project: Project, offset: Int, endOffset: Int, editor: Editor = null): Unit = {
    def handleOneProblem(problem: ProblemDescriptor): Unit = {
      val fixes = problem.getFixes.collect { case f: LocalQuickFixOnPsiElement => f }
      fixes.foreach(_.applyFix)
    }

    val intention = new RemoveBracesIntention
    val holder = new ProblemsHolder(InspectionManager.getInstance(project), file, false)

    val removeReturnVisitor = (new RemoveRedundantReturnInspection).buildVisitor(holder, isOnTheFly = false)
    val parenthesisedExpr = (new ScalaUnnecessaryParenthesesInspection).buildVisitor(holder, isOnTheFly = false)
    val removeSemicolon = (new ScalaUnnecessarySemicolonInspection).buildVisitor(holder, isOnTheFly = false)
    val addPrefix = (new ReferenceMustBePrefixedInspection).buildVisitor(holder, isOnTheFly = false)

    collectTopElements(offset, endOffset, file).foreach(_.depthFirst().foreach {
      case el: ScFunctionDefinition =>
        removeReturnVisitor.visitElement(el)
      case parentized: ScParenthesisedExpr =>
        parenthesisedExpr.visitElement(parentized)
      case semicolon: PsiElement if semicolon.getNode.getElementType == ScalaTokenTypes.tSEMICOLON =>
        removeSemicolon.visitElement(semicolon)
      case ref: ScReferenceElement if ref.qualifier.isEmpty && !ref.getParent.isInstanceOf[ScImportSelector] =>
        addPrefix.visitElement(ref)
      case el => intention.invoke(project, editor, el)
    })

    holder.getResults.forEach(handleOneProblem)
  }

  sealed trait Part

  case class ElementPart(elem: PsiElement) extends Part

  case class TextPart(text: String) extends Part

  def getTextBetweenOffsets(file: PsiFile, startOffsets: Array[Int], endOffsets: Array[Int]): String = {
    val builder = new java.lang.StringBuilder()
    val textGaps = startOffsets.zip(endOffsets).sortWith(_._1 < _._1)
    for ((start, end) <- textGaps) {
      if (start != end && start < end)
        builder.append(file.charSequence.subSequence(start, end))
    }
    builder.toString
  }

  def compareTextNEq(text1: String, text2: String): Boolean = {
    def textWithoutLastSemicolon(text: String) = {
      if (text != null && text.nonEmpty && text.last == ';') text.substring(0, text.length - 1)
      else text
    }

    textWithoutLastSemicolon(text1) != textWithoutLastSemicolon(text2)
  }

  class ConvertedCode(val data: String, val associations: Array[Association], val showDialog: Boolean = false) extends TextBlockTransferableData {
    def setOffsets(offsets: Array[Int], _index: Int): Int = {
      var index = _index
      for (association <- associations) {
        association.range = new TextRange(offsets(index), offsets(index + 1))
        index += 2
      }
      index
    }

    def getOffsets(offsets: Array[Int], _index: Int): Int = {
      var index = _index
      for (association <- associations) {
        offsets(index) = association.range.getStartOffset
        index += 1
        offsets(index) = association.range.getEndOffset
        index += 1
      }
      index
    }

    def getOffsetCount: Int = associations.length * 2

    def getFlavor: DataFlavor = ConvertedCode.Flavor
  }

  def replaceByConvertedCode(editor: Editor, bounds: RangeMarker, text: String): Unit = {
    val document = editor.getDocument

    def hasQuoteAt(offset: Int) = {
      val chars = document.getCharsSequence
      offset >= 0 && offset <= chars.length() && chars.charAt(offset) == '\"'
    }

    val start = bounds.getStartOffset
    val end = bounds.getEndOffset
    val isInsideStringLiteral = hasQuoteAt(start - 1) && hasQuoteAt(end)
    if (isInsideStringLiteral && text.startsWith("\"") && text.endsWith("\""))
      document.replaceString(start - 1, end + 1, text)
    else document.replaceString(start, end, text)
  }

  object ConvertedCode {
    lazy val Flavor: DataFlavor = new DataFlavor(classOf[ConvertedCode], "JavaToScalaConvertedCode")
  }

  def shownDialog(msg: String, project: Project): ScalaPasteFromJavaDialog = {
    val dialog = new ScalaPasteFromJavaDialog(project, msg)
    dialog.show()
    dialog
  }
}
