package org.jetbrains.plugins.scala
package conversion
package copy

import java.awt.datatransfer.{DataFlavor, Transferable}
import java.lang.Boolean
import java.util.Collections.singletonList

import com.intellij.codeInsight.editorActions._
import com.intellij.diagnostic.LogMessageEx
import com.intellij.openapi.diagnostic.{Attachment, Logger}
import com.intellij.openapi.editor.{Editor, RangeMarker}
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.util.{Ref, TextRange}
import com.intellij.psi.codeStyle.{CodeStyleManager, CodeStyleSettingsManager}
import com.intellij.psi.{PsiDocumentManager, PsiElement, PsiFile, PsiJavaFile}
import com.intellij.util.ExceptionUtil
import org.jetbrains.plugins.scala.conversion.ast.{JavaCodeReferenceStatement, LiteralExpression, MainConstruction, TypedElement}
import org.jetbrains.plugins.scala.conversion.visitors.SimplePrintVisitor
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.settings._

import scala.annotation.tailrec
import scala.collection.mutable.{ArrayBuffer, ListBuffer}

/**
 * User: Alexander Podkhalyuzin
 * Date: 30.11.2009
 */

class JavaCopyPastePostProcessor extends SingularCopyPastePostProcessor[TextBlockTransferableData] {
  private val Log = Logger.getInstance(classOf[JavaCopyPastePostProcessor])

  private lazy val referenceProcessor = Extensions.getExtensions(CopyPastePostProcessor.EP_NAME)
          .find(_.isInstanceOf[JavaCopyPasteReferenceProcessor]).get

  private lazy val scalaProcessor = Extensions.getExtensions(CopyPastePostProcessor.EP_NAME)
          .find(_.isInstanceOf[ScalaCopyPastePostProcessor]).get.asInstanceOf[ScalaCopyPastePostProcessor]

  protected def collectTransferableData0(file: PsiFile, editor: Editor, startOffsets: Array[Int], endOffsets: Array[Int]): TextBlockTransferableData = {
    if (DumbService.getInstance(file.getProject).isDumb) return null
    if (!ScalaProjectSettings.getInstance(file.getProject).isEnableJavaToScalaConversion ||
        !file.isInstanceOf[PsiJavaFile]) return null

    sealed trait Part
    case class ElementPart(elem: PsiElement) extends Part
    case class TextPart(text: String) extends Part

    try {
      def getTopElements: Seq[Part] = {
        val buffer = new ArrayBuffer[Part]
        for ((startOffset, endOffset) <- startOffsets.zip(endOffsets)) {
          @tailrec
          def findElem(offset: Int): PsiElement = {
            if (offset > endOffset) return null
            val elem = file.findElementAt(offset)
            if (elem == null) return null
            if (elem.getParent.getTextRange.getEndOffset > endOffset ||
              elem.getParent.getTextRange.getStartOffset < startOffset) findElem(elem.getTextRange.getEndOffset + 1)
            else elem
          }
          var elem: PsiElement = findElem(startOffset)
          if (elem != null) {
            while (elem.getParent != null && !elem.getParent.isInstanceOf[PsiFile] &&
              elem.getParent.getTextRange.getEndOffset <= endOffset &&
              elem.getParent.getTextRange.getStartOffset >= startOffset) {
              elem = elem.getParent
            }
            if (startOffset < elem.getTextRange.getStartOffset) {
              buffer += TextPart(new TextRange(startOffset, elem.getTextRange.getStartOffset).substring(file.getText))
            }
            buffer += ElementPart(elem)
            while (elem.getNextSibling != null && elem.getNextSibling.getTextRange.getEndOffset <= endOffset) {
              elem = elem.getNextSibling
              buffer += ElementPart(elem)
            }
            if (elem.getTextRange.getEndOffset < endOffset) {
              buffer += TextPart(new TextRange(elem.getTextRange.getEndOffset, endOffset).substring(file.getText))
            }
          }
        }
        buffer.toSeq
      }

      def getRefs: Seq[ReferenceData] = {
        val refs = {
          val data = referenceProcessor.collectTransferableData(file, editor, startOffsets, endOffsets)
          if (data.isEmpty) null else data.get(0).asInstanceOf[ReferenceTransferableData]
        }
        val shift = startOffsets.headOption.getOrElse(0)
        if (refs != null)
          refs.getData.map { it =>
            new ReferenceData(it.startOffset + shift, it.endOffset + shift, it.qClassName, it.staticMemberName)
          } else Seq.empty
      }

      val associationsHelper = new ListBuffer[AssociationHelper]()
      val resultNode = new MainConstruction
      val topElements = getTopElements
      val data = getRefs
      for (part <- topElements) {
        part match {
          case TextPart(s) =>
            resultNode.addChild(LiteralExpression(s))
          case ElementPart(element) =>
            val result = JavaToScala.convertPsiToIntermdeiate(element, null)(associationsHelper, data, withComments = true)
            resultNode.addChild(result)
        }
      }

      val visitor = new SimplePrintVisitor
      visitor.visit(resultNode)
      val text = visitor.stringResult
      val rangeMap = visitor.rangedElementsMap

      val updatedAssociations = associationsHelper.filter(_.itype.isInstanceOf[TypedElement]).
        map { a =>
          val typedElement = a.itype.asInstanceOf[TypedElement].getType
          val range = rangeMap.getOrElse(typedElement, new TextRange(0, 0))
          new Association(a.kind, range, a.path)
        }

      updatedAssociations ++= associationsHelper.filter(_.itype.isInstanceOf[JavaCodeReferenceStatement]).
        map { a =>
          val range = rangeMap.getOrElse(a.itype, new TextRange(0, 0))
          new Association(a.kind, range, a.path)
        }

      new ConvertedCode(text, updatedAssociations.toArray)
    } catch {
      case e: Exception =>
        val selections = (startOffsets, endOffsets).zipped.map((a, b) => file.getText.substring(a, b))
        val attachments = selections.zipWithIndex.map(p => new Attachment("Selection-%d.java".format(p._2 + 1), p._1))
        Log.error(LogMessageEx.createEvent(e.getMessage, ExceptionUtil.getThrowableText(e), attachments: _*))
        null
    }
  }

  protected def extractTransferableData0(content: Transferable): TextBlockTransferableData = {
    if (content.isDataFlavorSupported(ConvertedCode.Flavor))
      content.getTransferData(ConvertedCode.Flavor).asInstanceOf[TextBlockTransferableData]
    else
      null
  }

  protected def processTransferableData0(project: Project, editor: Editor, bounds: RangeMarker, i: Int, ref: Ref[Boolean], value: TextBlockTransferableData) {
    if (!ScalaProjectSettings.getInstance(project).isEnableJavaToScalaConversion) return
    if (value == null) return
    val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument)
    if (!file.isInstanceOf[ScalaFile]) return
    val dialog = new ScalaPasteFromJavaDialog(project)
    val (text, associations) = value match {
      case code: ConvertedCode => (code.data, code.associations)
      case _ => ("", Array.empty[Association])
    }
    if (text == "") return //copy as usually
    if (!ScalaProjectSettings.getInstance(project).isDontShowConversionDialog) dialog.show()
    if (ScalaProjectSettings.getInstance(project).isDontShowConversionDialog || dialog.isOK) {
      val shiftedAssociations = inWriteAction {
        replaceByConvertedCode(editor, bounds, text)
        editor.getCaretModel.moveToOffset(bounds.getStartOffset + text.length)
        PsiDocumentManager.getInstance(file.getProject).commitDocument(editor.getDocument)

        val markedAssociations = associations.toList.zipMapped { dependency =>
          editor.getDocument.createRangeMarker(dependency.range.shiftRight(bounds.getStartOffset))
        }

        withSpecialStyleIn(project) {
          val manager = CodeStyleManager.getInstance(project)
          manager.reformatText(file, bounds.getStartOffset, bounds.getStartOffset + text.length)
        }

        markedAssociations.map {
          case (association, marker) =>
            val movedAssociation = association.copy(range = new TextRange(marker.getStartOffset - bounds.getStartOffset,
              marker.getEndOffset - bounds.getStartOffset))
            marker.dispose()
            movedAssociation
        }
      }
      scalaProcessor.processTransferableData(project, editor, bounds, i, ref, singletonList(new Associations(shiftedAssociations)))
    }
  }

  private def withSpecialStyleIn(project: Project)(block: => Unit) {
    val settings = CodeStyleSettingsManager.getSettings(project).getCommonSettings(ScalaFileType.SCALA_LANGUAGE)

    val keep_blank_lines_in_code = settings.KEEP_BLANK_LINES_IN_CODE
    val keep_blank_lines_in_declarations = settings.KEEP_BLANK_LINES_IN_DECLARATIONS
    val keep_blank_lines_before_rbrace = settings.KEEP_BLANK_LINES_BEFORE_RBRACE

    settings.KEEP_BLANK_LINES_IN_CODE = 0
    settings.KEEP_BLANK_LINES_IN_DECLARATIONS = 0
    settings.KEEP_BLANK_LINES_BEFORE_RBRACE = 0

    try {
      block
    }
    finally {
      settings.KEEP_BLANK_LINES_IN_CODE = keep_blank_lines_in_code
      settings.KEEP_BLANK_LINES_IN_DECLARATIONS = keep_blank_lines_in_declarations
      settings.KEEP_BLANK_LINES_BEFORE_RBRACE = keep_blank_lines_before_rbrace
    }
  }

  def replaceByConvertedCode(editor: Editor, bounds: RangeMarker, text: String) = {
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

  class ConvertedCode(val data: String, val associations: Array[Association]) extends TextBlockTransferableData {
    def setOffsets(offsets: Array[Int], _index: Int) = {
      var index = _index
      for (association <- associations) {
        association.range = new TextRange(offsets(index), offsets(index + 1))
        index += 2
      }
      index
    }

    def getOffsets(offsets: Array[Int], _index: Int) = {
      var index = _index
      for (association <- associations) {
        offsets(index) = association.range.getStartOffset
        index += 1
        offsets(index) = association.range.getEndOffset
        index += 1
      }
      index
    }

    def getOffsetCount = associations.length * 2

    def getFlavor: DataFlavor = ConvertedCode.Flavor
  }

  object ConvertedCode {
    lazy val Flavor: DataFlavor = new DataFlavor(classOf[ConvertedCode], "JavaToScalaConvertedCode")
  }

}