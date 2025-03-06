package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import org.jetbrains.jps.incremental.scala.MessageKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test;

class CompilerMessageKindsTest {

  @Test
  def wrongRef1(): Unit = {
    val text = "Value myValue is not a member of MyType"
    val kind = MessageKind.Error
    val expected = HighlightInfoType.WRONG_REF
    val actual = CompilerMessageKinds.highlightInfoType(kind, text, scalacOptions = Seq.empty)
    assertEquals(expected, actual)
  }

  @Test
  def wrongRef2(): Unit = {
    val text = "Not found: myValue"
    val kind = MessageKind.Error
    val expected = HighlightInfoType.WRONG_REF
    val actual = CompilerMessageKinds.highlightInfoType(kind, text, scalacOptions = Seq.empty)
    assertEquals(expected, actual)
  }

  @Test
  def wrongRef3(): Unit = {
    val text = "Cannot find symbol myValue"
    val kind = MessageKind.Error
    val expected = HighlightInfoType.WRONG_REF
    val actual = CompilerMessageKinds.highlightInfoType(kind, text, scalacOptions = Seq.empty)
    assertEquals(expected, actual)
  }

  @Test
  def errorUnusedImportFatalWarningsWithFlag(): Unit = {
    val text = "Unused import"
    val scalacOptions = Seq("-Xfatal-warnings", "-Wunused:privates,locals", "-Wunused:explicits,imports,implicits", "-Wunused:params,patvars")
    val kind = MessageKind.Error
    val expected = HighlightInfoType.ERROR
    val actual = CompilerMessageKinds.highlightInfoType(kind, text, scalacOptions)
    assertEquals(expected, actual)
  }

  @Test
  def errorUnusedImportWerrorWithFlag(): Unit = {
    val text = "Unused import"
    val scalacOptions = Seq("-Werror", "-Wunused:privates,locals", "-Wunused:explicits,imports,implicits", "-Wunused:params,patvars")
    val kind = MessageKind.Error
    val expected = HighlightInfoType.ERROR
    val actual = CompilerMessageKinds.highlightInfoType(kind, text, scalacOptions)
    assertEquals(expected, actual)
  }

  @Test
  def errorUnusedImportNoFlag(): Unit = {
    val text = "Unused import"
    val scalacOptions = Seq("-Xfatal-warnings", "-Wunused:privates,locals", "-Wunused:params,patvars")
    val kind = MessageKind.Error
    val expected = HighlightInfoType.UNUSED_SYMBOL
    val actual = CompilerMessageKinds.highlightInfoType(kind, text, scalacOptions)
    assertEquals(expected, actual)
  }

  @Test
  def errorUnusedImportNoFatalWarningsNoFlag(): Unit = {
    val text = "Unused import"
    val scalacOptions = Seq("-Wunused:privates,locals", "-Wunused:params,patvars")
    val kind = MessageKind.Error
    val expected = HighlightInfoType.UNUSED_SYMBOL
    val actual = CompilerMessageKinds.highlightInfoType(kind, text, scalacOptions)
    assertEquals(expected, actual)
  }

  @Test
  def regularError(): Unit = {
    val text = "Some random compiler error"
    val kind = MessageKind.Error
    val expected = HighlightInfoType.ERROR
    val actual = CompilerMessageKinds.highlightInfoType(kind, text, scalacOptions = Seq.empty)
    assertEquals(expected, actual)
  }

  @Test
  def warningUnusedImportFatalWarningsWithFlag(): Unit = {
    val text = "Unused import"
    val scalacOptions = Seq("-Xfatal-warnings", "-Wunused:privates,locals", "-Wunused:explicits,imports,implicits", "-Wunused:params,patvars")
    val kind = MessageKind.Warning
    val expected = HighlightInfoType.WARNING
    val actual = CompilerMessageKinds.highlightInfoType(kind, text, scalacOptions)
    assertEquals(expected, actual)
  }

  @Test
  def warningUnusedImportWerrorWithFlag(): Unit = {
    val text = "Unused import"
    val scalacOptions = Seq("-Werror", "-Wunused:privates,locals", "-Wunused:explicits,imports,implicits", "-Wunused:params,patvars")
    val kind = MessageKind.Warning
    val expected = HighlightInfoType.WARNING
    val actual = CompilerMessageKinds.highlightInfoType(kind, text, scalacOptions)
    assertEquals(expected, actual)
  }

  @Test
  def warningUnusedImportNoFlag(): Unit = {
    val text = "Unused import"
    val scalacOptions = Seq("-Xfatal-warnings", "-Wunused:privates,locals", "-Wunused:params,patvars")
    val kind = MessageKind.Warning
    val expected = HighlightInfoType.UNUSED_SYMBOL
    val actual = CompilerMessageKinds.highlightInfoType(kind, text, scalacOptions)
    assertEquals(expected, actual)
  }

  @Test
  def warningUnusedImportNoFatalWarningsNoFlag(): Unit = {
    val text = "Unused import"
    val scalacOptions = Seq("-Wunused:privates,locals", "-Wunused:params,patvars")
    val kind = MessageKind.Warning
    val expected = HighlightInfoType.UNUSED_SYMBOL
    val actual = CompilerMessageKinds.highlightInfoType(kind, text, scalacOptions)
    assertEquals(expected, actual)
  }

  @Test
  def regularWarning(): Unit = {
    val text = "Some random compiler warning"
    val kind = MessageKind.Warning
    val expected = HighlightInfoType.WARNING
    val actual = CompilerMessageKinds.highlightInfoType(kind, text, scalacOptions = Seq.empty)
    assertEquals(expected, actual)
  }

  @Test
  def regularInfo(): Unit = {
    val text = "Some random compiler info"
    val kind = MessageKind.Info
    val expected = HighlightInfoType.WEAK_WARNING
    val actual = CompilerMessageKinds.highlightInfoType(kind, text, scalacOptions = Seq.empty)
    assertEquals(expected, actual)
  }

  @Test
  def other1(): Unit = {
    val text = "Some random compiler internal builder error"
    val kind = MessageKind.InternalBuilderError
    val expected = HighlightInfoType.INFORMATION
    val actual = CompilerMessageKinds.highlightInfoType(kind, text, scalacOptions = Seq.empty)
    assertEquals(expected, actual)
  }

  @Test
  def other2(): Unit = {
    val text = "Some random jps info"
    val kind = MessageKind.JpsInfo
    val expected = HighlightInfoType.INFORMATION
    val actual = CompilerMessageKinds.highlightInfoType(kind, text, scalacOptions = Seq.empty)
    assertEquals(expected, actual)
  }

  @Test
  def other3(): Unit = {
    val text = "Some random progress message"
    val kind = MessageKind.Progress
    val expected = HighlightInfoType.INFORMATION
    val actual = CompilerMessageKinds.highlightInfoType(kind, text, scalacOptions = Seq.empty)
    assertEquals(expected, actual)
  }

  @Test
  def other4(): Unit = {
    val text = "Some random other message"
    val kind = MessageKind.Other
    val expected = HighlightInfoType.INFORMATION
    val actual = CompilerMessageKinds.highlightInfoType(kind, text, scalacOptions = Seq.empty)
    assertEquals(expected, actual)
  }
}