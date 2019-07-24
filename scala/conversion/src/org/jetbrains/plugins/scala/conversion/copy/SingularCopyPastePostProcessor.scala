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
import org.jetbrains.plugins.scala.extensions.ElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
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
                                             startOffsets: Array[Int], endOffsets: Array[Int]): ju.List[T] =
    collectTransferableData(startOffsets, endOffsets)(file, editor).fold(emptyList[T]())(singletonList)

  def collectTransferableData(startOffsets: Array[Int], endOffsets: Array[Int])
                             (implicit file: PsiFile, editor: Editor): Option[T] = None

  override final def extractTransferableData(content: Transferable): ju.List[T] =
    extractTransferableDataImpl(content).fold(emptyList[T]()) { value =>
      singletonList(value.asInstanceOf[T])
    }

  protected def extractTransferableDataImpl(content: Transferable): Option[AnyRef] =
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
        Option(scalaFile.findElementAt(caretOffset)) match {
          case Some(ElementType(ScalaTokenTypes.tSTRING | ScalaTokenTypes.tMULTILINE_STRING)) => //do nothing
          case _ =>
            values.forEach {
              processTransferableData(bounds, caretOffset, ref, _)(project, editor, scalaFile)
            }
        }
      case _ =>
    }

  def processTransferableData(bounds: RangeMarker, caretOffset: Int,
                              ref: Ref[JBoolean], value: T)
                             (implicit project: Project,
                              editor: Editor,
                              file: ScalaFile): Unit
}