package org.jetbrains.plugins.scala
package conversion
package copy

import java.awt.datatransfer.Transferable
import java.lang.Boolean
import java.util.Collections.singletonList

import com.intellij.codeInsight.editorActions._
import com.intellij.diagnostic.LogMessageEx
import com.intellij.openapi.diagnostic.{Attachment, Logger}
import com.intellij.openapi.editor.{Editor, RangeMarker}
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.util.{Ref, TextRange}
import com.intellij.psi._
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.util.ExceptionUtil
import org.jetbrains.plugins.scala.conversion.ConverterUtil.{ElementPart, TextPart}
import org.jetbrains.plugins.scala.conversion.ast.{JavaCodeReferenceStatement, LiteralExpression, MainConstruction, TypedElement}
import org.jetbrains.plugins.scala.conversion.visitors.PrintWithComments
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.settings._

import scala.collection.mutable.ListBuffer

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

    try {
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
      val (topElements, dropElements) = ConverterUtil.getTopElements(file, startOffsets, endOffsets)
      val data = getRefs
      for (part <- topElements) {
        part match {
          case TextPart(s) =>
            resultNode.addChild(LiteralExpression(s))
          case ElementPart(comment: PsiComment) =>
            if (!dropElements.contains(comment))
              resultNode.addChild(LiteralExpression(comment.getText))
            dropElements.add(comment)
          case ElementPart(element) =>
            val result = JavaToScala.convertPsiToIntermdeiate(element, null)(associationsHelper, data, dropElements, textMode = false)
            resultNode.addChild(result)
        }
      }

      val visitor = new PrintWithComments
      visitor.visit(resultNode)
      val text = visitor.stringResult
      val rangeMap = visitor.rangedElementsMap

      val updatedAssociations = associationsHelper.filter(_.itype.isInstanceOf[TypedElement]).
        map { a =>
          val typedElement = a.itype.asInstanceOf[TypedElement].getType
          val range = rangeMap.getOrElse(typedElement, new TextRange(0, 0))
          Association(a.kind, range, a.path)
        }

      updatedAssociations ++= associationsHelper.filter(_.itype.isInstanceOf[JavaCodeReferenceStatement]).
        map { a =>
          val range = rangeMap.getOrElse(a.itype, new TextRange(0, 0))
          Association(a.kind, range, a.path)
        }

      val oldText = ConverterUtil.getTextBetweenOffsets(file, startOffsets, endOffsets)
      new ConverterUtil.ConvertedCode(text, updatedAssociations.toArray, ConverterUtil.compareTextNEq(oldText, text))
    } catch {
      case e: Exception =>
        val charSequence = file.charSequence
        val selections = (startOffsets, endOffsets).zipped.map((a, b) => charSequence.substring(a, b))
        val attachments = selections.zipWithIndex.map(p => new Attachment("Selection-%d.java".format(p._2 + 1), p._1))
        Log.error(LogMessageEx.createEvent(e.getMessage, ExceptionUtil.getThrowableText(e), attachments: _*))
        null
    }
  }

  protected def extractTransferableData0(content: Transferable): TextBlockTransferableData = {
    if (content.isDataFlavorSupported(ConverterUtil.ConvertedCode.Flavor))
      content.getTransferData(ConverterUtil.ConvertedCode.Flavor).asInstanceOf[TextBlockTransferableData]
    else
      null
  }

  protected def processTransferableData0(project: Project, editor: Editor, bounds: RangeMarker, i: Int, ref: Ref[Boolean], value: TextBlockTransferableData) {
    if (!ScalaProjectSettings.getInstance(project).isEnableJavaToScalaConversion) return
    if (value == null) return
    val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument)
    if (!file.isInstanceOf[ScalaFile]) return

    val (text, associations, showDialog) = value match {
      case code: ConverterUtil.ConvertedCode => (code.data, code.associations, code.showDialog)
      case _ => ("", Array.empty[Association], true)
    }
    if (text == "") return
    //copy as usually
    val needShowDialog = (!ScalaProjectSettings.getInstance(project).isDontShowConversionDialog) && showDialog
    if (!needShowDialog || ConverterUtil.shownDialog(ScalaBundle.message("scala.copy.from.java"), project).isOK) {
      val shiftedAssociations = inWriteAction {
        ConverterUtil.performePaste(editor, bounds, text, project)

        val markedAssociations = associations.toList.zipMapped { dependency =>
          editor.getDocument.createRangeMarker(dependency.range.shiftRight(bounds.getStartOffset))
        }

        CodeStyleManager.getInstance(project)
          .reformatText(file, bounds.getStartOffset, bounds.getStartOffset + text.length)

        markedAssociations.map {
          case (association, marker) =>
            val movedAssociation = association.copy(range = new TextRange(marker.getStartOffset - bounds.getStartOffset,
              marker.getEndOffset - bounds.getStartOffset))
            marker.dispose()
            movedAssociation
        }
      }

      scalaProcessor.processTransferableData(project, editor, bounds, i, ref, singletonList(new Associations(shiftedAssociations)))

      inWriteAction {
        ConverterUtil.cleanCode(file, project, bounds.getStartOffset, bounds.getEndOffset, editor)
      }
    }
  }
}