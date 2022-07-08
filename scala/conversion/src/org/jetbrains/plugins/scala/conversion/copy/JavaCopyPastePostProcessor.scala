package org.jetbrains.plugins.scala
package conversion
package copy

import java.lang.Boolean
import com.intellij.codeInsight.editorActions._
import com.intellij.openapi.diagnostic.{Attachment, ControlFlowException, Logger}
import com.intellij.openapi.editor.{Editor, RangeMarker}
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.util.{Ref, TextRange}
import com.intellij.psi._
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.plugins.scala.conversion.ast.{LiteralExpression, MainConstruction, TypedElement}
import org.jetbrains.plugins.scala.conversion.copy.ScalaPasteFromJavaDialog.CopyFrom
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.refactoring._
import org.jetbrains.plugins.scala.settings._

import scala.collection.mutable

class JavaCopyPastePostProcessor extends SingularCopyPastePostProcessor[ConverterUtil.ConvertedCode](ConverterUtil.ConvertedCode.flavor) {

  import ConverterUtil._

  private val Log = Logger.getInstance(classOf[JavaCopyPastePostProcessor])

  private lazy val referenceProcessor = CopyPastePostProcessor.EP_NAME.findExtensionOrFail(classOf[JavaCopyPasteReferenceProcessor])
  private lazy val scalaProcessor = CopyPastePostProcessor.EP_NAME.findExtensionOrFail(classOf[ScalaCopyPastePostProcessor])

  override def collectTransferableData(startOffsets: Array[Int], endOffsets: Array[Int])
                                      (implicit file: PsiFile, editor: Editor): Option[ConvertedCode] = {
    if (DumbService.getInstance(file.getProject).isDumb) return None
    if (!ScalaProjectSettings.getInstance(file.getProject).isEnableJavaToScalaConversion ||
      !file.isInstanceOf[PsiJavaFile]) return None

    try {
      val data: Seq[ReferenceData] =
        referenceProcessor.collectTransferableData(file, editor, startOffsets, endOffsets) match {
          case dataList if dataList.isEmpty => Seq.empty
          case dataList =>
            val shift = startOffsets.headOption.getOrElse(0)
            dataList.get(0).getData.map { it =>
              new ReferenceData(
                it.startOffset + shift,
                it.endOffset + shift,
                it.qClassName,
                it.staticMemberName
              )
            }.toIndexedSeq
        }

      import JavaToScala._
      val associationsHelper = mutable.ListBuffer.empty[AssociationHelper]
      val resultNode = new MainConstruction
      val (topElements, dropElements) = getTopElements(file, startOffsets, endOffsets)
      for (part <- topElements) {
        part match {
          case TextPart(s) =>
            resultNode.addChild(LiteralExpression(s))
          case ElementPart(comment: PsiComment) =>
            if (!dropElements.contains(comment))
              resultNode.addChild(LiteralExpression(comment.getText))
            dropElements.add(comment)
          case ElementPart(element) =>
            val result = convertPsiToIntermediate(element, null)(associationsHelper, data, dropElements, textMode = false)
            resultNode.addChild(result)
        }
      }

      val visitor = visitors.PrintWithComments(resultNode)

      val updatedAssociations = associationsHelper.collect {
        case AssociationHelper(itype: TypedElement, path) => Association(path, visitor(itype.getType))
      } ++ associationsHelper.collect {
        case AssociationHelper(itype, path) => Association(path, visitor(itype))
      }

      val text = visitor()
      val oldText = getTextBetweenOffsets(file, startOffsets, endOffsets)
      val result = ConvertedCode(
        updatedAssociations.toArray,
        text,
        compareTextNEq(oldText, text)
      )
      Some(result)
    } catch {
      case c: ControlFlowException => throw c
      case e: Exception =>
        val charSequence = file.charSequence
        val selections = (startOffsets lazyZip endOffsets).map((a, b) => charSequence.substring(a, b))
        val attachments = selections.zipWithIndex.map(p => new Attachment("Selection-%d.java".format(p._2 + 1), p._1))
        Log.error(e.getMessage, e, attachments.toSeq: _*)
        None
    }
  }

  override def processTransferableData(bounds: RangeMarker, caretOffset: Int,
                                       ref: Ref[_ >: Boolean], value: ConvertedCode)
                                      (implicit project: Project,
                                       editor: Editor,
                                       file: ScalaFile): Unit = {
    val settings: ScalaProjectSettings = ScalaProjectSettings.getInstance(project)
    if (!settings.isEnableJavaToScalaConversion) return

    if (value == null) return
    val ConvertedCode(associations, text, showDialog) = value
    if (text == "") return
    //copy as usually

    if (!showDialog || ScalaPasteFromJavaDialog.showAndGet(CopyFrom.JavaFile, project)) {
      val shiftedAssociations = inWriteAction {
        performePaste(editor, bounds, text, project)

        val markedAssociations = associations.toSeq.zipMapped { dependency =>
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

      scalaProcessor.processTransferableData(bounds, caretOffset, ref, Associations(shiftedAssociations.toArray))

      inWriteAction {
        cleanCode(file, project, bounds.getStartOffset, bounds.getEndOffset, editor)
      }
    }
  }
}