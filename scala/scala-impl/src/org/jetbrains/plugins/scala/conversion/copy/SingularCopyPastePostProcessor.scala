package org.jetbrains.plugins.scala
package conversion
package copy

import java.awt.datatransfer.{DataFlavor, Transferable}
import java.{util => ju}

import com.intellij.codeInsight.editorActions.{CopyPastePostProcessor, TextBlockTransferableData}
import com.intellij.openapi.editor.{Editor, RangeMarker}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

/**
  * @author Pavel Fatin
  *
  *         Common adapter for legacy interface implementations.
  */
abstract class SingularCopyPastePostProcessor[T <: TextBlockTransferableData](dataFlavor: DataFlavor)
  extends CopyPastePostProcessor[T] {

  import ju.Collections._

  override final def collectTransferableData(file: PsiFile, editor: Editor,
                                             startOffsets: Array[Int], endOffsets: Array[Int]): ju.List[T] = {

    val result = collectTransferableData0(file, editor, startOffsets, endOffsets)

    if (result == null) emptyList() else singletonList(result)
  }

  protected def collectTransferableData0(file: PsiFile, editor: Editor,
                                         startOffsets: Array[Int], endOffsets: Array[Int]): T

  override final def extractTransferableData(content: Transferable): ju.List[T] =
    extractTransferableData0(content).fold(emptyList[T]()) { value =>
      singletonList(value.asInstanceOf[T])
    }

  protected def extractTransferableData0(content: Transferable): Option[AnyRef] =
    dataFlavor match {
      case flavor if content.isDataFlavorSupported(flavor) => Some(content.getTransferData(flavor))
      case _ => None
    }

  import java.lang.{Boolean => JBoolean}

  override final def processTransferableData(project: Project, editor: Editor,
                                             bounds: RangeMarker, caretOffset: Int,
                                             ref: Ref[JBoolean], values: ju.List[T]): Unit =
    PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument) match {
      case scalaFile: ScalaFile =>
        values.forEach {
          processTransferableData(bounds, caretOffset, ref, _)(project, editor, scalaFile)
        }
      case _ =>
    }

  protected def processTransferableData(bounds: RangeMarker, caretOffset: Int,
                                        ref: Ref[JBoolean], value: T)
                                       (implicit project: Project,
                                        editor: Editor,
                                        file: ScalaFile): Unit
}