package org.jetbrains.plugins.scala.conversion.copy

import com.intellij.codeInsight.editorActions.{CopyPastePostProcessor, TextBlockTransferableData}
import com.intellij.openapi.editor.{Editor, RangeMarker}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import org.jetbrains.plugins.scala.extensions.ElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

import java.awt.datatransfer.{DataFlavor, Transferable}
import java.lang.{Boolean => JBoolean}
import java.{util => ju}

/**
 * Common adapter for legacy interface implementations.
 */
abstract class SingularCopyPastePostProcessor[T <: TextBlockTransferableData](dataFlavor: DataFlavor)
  extends CopyPastePostProcessor[T] {

  import ju.Collections._

  override final def collectTransferableData(
    file: PsiFile,
    editor: Editor,
    startOffsets: Array[Int],
    endOffsets: Array[Int]
  ): ju.List[T] = {
    val transferableData = collectTransferableData(startOffsets, endOffsets)(file, editor)
    transferableData.fold(emptyList[T]())(singletonList)
  }

  protected def collectTransferableData(
    startOffsets: Array[Int],
    endOffsets: Array[Int]
  )(implicit file: PsiFile, editor: Editor): Option[T]

  override final def extractTransferableData(content: Transferable): ju.List[T] = {
    val transferableData = extractTransferableDataImpl(content)
    transferableData.fold(emptyList[T]()) { value =>
      singletonList(value.asInstanceOf[T])
    }
  }

  protected def extractTransferableDataImpl(content: Transferable): Option[AnyRef] =
    if (content.isDataFlavorSupported(dataFlavor))
      Some(content.getTransferData(dataFlavor))
    else
      None

  override final def processTransferableData(
    project: Project,
    editor: Editor,
    bounds: RangeMarker,
    caretOffset: Int,
    ref: Ref[_ >: JBoolean],
    values: ju.List[_ <: T]
  ): Unit = {
    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument)
    psiFile match {
      case scalaFile: ScalaFile =>
        val element = scalaFile.findElementAt(caretOffset)
        val isStringLiteral = element != null && ScalaTokenTypes.STRING_LITERAL_TOKEN_SET.contains(element.getNode.getElementType)
        if (isStringLiteral) {
          //skip
        }
        else {
          values.forEach {
            processTransferableData(bounds, caretOffset, ref, _)(project, editor, scalaFile)
          }
        }
      case _ =>
    }
  }

  protected def processTransferableData(
    bounds: RangeMarker,
    caretOffset: Int,
    ref: Ref[_ >: JBoolean],
    value: T
  )(implicit
    project: Project,
    editor: Editor,
    file: ScalaFile
  ): Unit
}