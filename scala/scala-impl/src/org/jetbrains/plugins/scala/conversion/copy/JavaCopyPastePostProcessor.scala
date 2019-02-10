package org.jetbrains.plugins.scala
package conversion
package copy

import java.awt.datatransfer.Transferable
import java.lang.Boolean
import java.{util => ju}

import com.intellij.codeInsight.editorActions._
import com.intellij.openapi.diagnostic.{Attachment, Logger}
import com.intellij.openapi.editor.{Editor, RangeMarker}
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.util.{Ref, TextRange}
import com.intellij.psi._
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.plugins.scala.conversion.ast.{LiteralExpression, MainConstruction, TypedElement}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.refactoring.AssociationsData.Association
import org.jetbrains.plugins.scala.settings._

import scala.collection.mutable

/**
  * User: Alexander Podkhalyuzin
  * Date: 30.11.2009
  */
class JavaCopyPastePostProcessor extends SingularCopyPastePostProcessor[ConverterUtil.ConvertedCode] {

  import ConverterUtil._

  private val Log = Logger.getInstance(classOf[JavaCopyPastePostProcessor])

  private lazy val referenceProcessor = CopyPastePostProcessor.EP_NAME.findExtensionOrFail(classOf[JavaCopyPasteReferenceProcessor])
  private lazy val scalaProcessor = CopyPastePostProcessor.EP_NAME.findExtensionOrFail(classOf[ScalaCopyPastePostProcessor])

  protected def collectTransferableData0(file: PsiFile, editor: Editor,
                                         startOffsets: Array[Int], endOffsets: Array[Int]): ConvertedCode = {
    if (DumbService.getInstance(file.getProject).isDumb) return null
    if (!ScalaProjectSettings.getInstance(file.getProject).isEnableJavaToScalaConversion ||
      !file.isInstanceOf[PsiJavaFile]) return null

    try {
      def getRefs: Seq[ReferenceData] = {
        val refs = {
          val data = referenceProcessor.collectTransferableData(file, editor, startOffsets, endOffsets)
          if (data.isEmpty) null else data.get(0)
        }
        val shift = startOffsets.headOption.getOrElse(0)
        if (refs != null)
          refs.getData.map { it =>
            new ReferenceData(it.startOffset + shift, it.endOffset + shift, it.qClassName, it.staticMemberName)
          } else Seq.empty
      }

      val associationsHelper = mutable.ListBuffer.empty[AssociationHelper]
      val resultNode = new MainConstruction
      val (topElements, dropElements) = getTopElements(file, startOffsets, endOffsets)
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

      val visitor = visitors.PrintWithComments(resultNode)

      val updatedAssociations = associationsHelper.collect {
        case AssociationHelper(kind, itype: TypedElement, path) => Association(kind, path, visitor(itype.getType))
      } ++ associationsHelper.collect {
        case AssociationHelper(kind, itype, path) => Association(kind, path, visitor(itype))
      }

      val text = visitor()
      val oldText = getTextBetweenOffsets(file, startOffsets, endOffsets)
      new ConvertedCode(
        updatedAssociations.toArray,
        text,
        compareTextNEq(oldText, text)
      )
    } catch {
      case e: Exception =>
        val charSequence = file.charSequence
        val selections = (startOffsets, endOffsets).zipped.map((a, b) => charSequence.substring(a, b))
        val attachments = selections.zipWithIndex.map(p => new Attachment("Selection-%d.java".format(p._2 + 1), p._1))
        Log.error(e.getMessage, e, attachments: _*)
        null
    }
  }

  protected def extractTransferableData0(content: Transferable): ConvertedCode = ConvertedCode.flavor match {
    case flavor if content.isDataFlavorSupported(flavor) => content.getTransferData(flavor).asInstanceOf[ConvertedCode]
    case _ => null
  }

  protected def processTransferableData0(project: Project, editor: Editor,
                                         bounds: RangeMarker,
                                         i: Int, ref: Ref[Boolean],
                                         value: ConvertedCode): Unit = {
    val settings = ScalaProjectSettings.getInstance(project)
    if (!settings.isEnableJavaToScalaConversion) return
    if (value == null) return

    val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument)
    if (!file.isInstanceOf[ScalaFile]) return

    val ConvertedCode(associations, text, showDialog) = value
    if (text == "") return
    //copy as usually
    val needShowDialog = (!settings.isDontShowConversionDialog) && showDialog

    if (!needShowDialog || shownDialog(ScalaBundle.message("scala.copy.from.java"), project).isOK) {
      val shiftedAssociations = inWriteAction {
        performePaste(editor, bounds, text, project)

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

      scalaProcessor.processTransferableData(project, editor, bounds, i, ref, ju.Collections.singletonList(Associations(shiftedAssociations.toArray)))

      inWriteAction {
        cleanCode(file, project, bounds.getStartOffset, bounds.getEndOffset, editor)
      }
    }
  }
}