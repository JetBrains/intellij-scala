package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.compiler.CompilerMessageImpl
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.compiler.{CompilerMessage, CompilerMessageCategory}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile

trait Highlightable[T] {
  def severity(t: T): HighlightSeverity

  def message(t: T): String

  def range(t: T, editor: Editor): Option[TextRange]

  def offset(t: T, editor: Editor): Option[Int]

  def virtualFile(t: T): VirtualFile
}

object Highlightable {
  def apply[T: Highlightable]: Highlightable[T] =
    implicitly[Highlightable[T]]

  def severity[T: Highlightable](t: T): HighlightSeverity =
    Highlightable[T].severity(t)

  def message[T: Highlightable](t: T): String =
    Highlightable[T].message(t)

  def range[T: Highlightable](t: T, editor: Editor): Option[TextRange] =
    Highlightable[T].range(t, editor)

  def offset[T: Highlightable](t: T, editor: Editor): Option[Int] =
    Highlightable[T].offset(t, editor)

  def virtualFile[T: Highlightable](t: T): VirtualFile =
    Highlightable[T].virtualFile(t)
}