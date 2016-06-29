package org.jetbrains.plugins.scala
package conversion.copy

import java.awt.datatransfer.Transferable
import java.util.Collections._
import java.{lang, util}

import com.intellij.codeInsight.editorActions.{CopyPastePostProcessor, TextBlockTransferableData}
import com.intellij.openapi.editor.{Editor, RangeMarker}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.{NotNull, Nullable}

import scala.collection.JavaConverters._


/**
 * @author Pavel Fatin
 *
 * Common adapter for legacy interface implementations.
 */
abstract class SingularCopyPastePostProcessor[T <: TextBlockTransferableData] extends CopyPastePostProcessor[T] {
  @NotNull
  override final def collectTransferableData(file: PsiFile, editor: Editor,
                                             startOffsets: Array[Int], endOffsets: Array[Int]): util.List[T] = {

    val result = collectTransferableData0(file, editor, startOffsets, endOffsets)

    if (result == null) emptyList() else singletonList(result)
  }

  @Nullable
  protected def collectTransferableData0(file: PsiFile, editor: Editor,
                                         startOffsets: Array[Int], endOffsets: Array[Int]): T

  @NotNull
  override final def extractTransferableData(content: Transferable): util.List[T] = {
    val result = extractTransferableData0(content)

    if (result == null) emptyList() else singletonList(result)
  }

  @Nullable
  protected def extractTransferableData0(content: Transferable): T


  override final def processTransferableData(project: Project, editor: Editor, bounds: RangeMarker,
                                             caretOffset: Int, ref: Ref[lang.Boolean], values: util.List[T]) {

    values.asScala.foreach { value =>
      processTransferableData0(project, editor, bounds, caretOffset, ref, value)
    }
  }

  protected def processTransferableData0(project: Project, editor: Editor, bounds: RangeMarker,
                                         caretOffset: Int, ref: Ref[lang.Boolean], value: T)
}