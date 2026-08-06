package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.compiler.{CompilerMessage, CompilerMessageCategory}
import org.junit.Assert.{assertNotNull, assertTrue}

object CompilerMessagesUtil {

  def assertNoErrors(messages: Seq[CompilerMessage]): Unit = {
    val errors = messages.filter(_.getCategory == CompilerMessageCategory.ERROR)
    assertTrue(s"Expected no compilation errors, got: ${errors.mkString(System.lineSeparator())}", errors.isEmpty)
  }

  def assertNoErrorsOrWarnings(messages: Seq[CompilerMessage]): Unit = {
    val errorsAndWarnings = messages.filter { message =>
      val category = message.getCategory
      category == CompilerMessageCategory.ERROR || category == CompilerMessageCategory.WARNING
    }
    assertTrue(s"Expected no compilation errors or warnings, got: ${errorsAndWarnings.mkString(System.lineSeparator())}", errorsAndWarnings.isEmpty)
  }

  def assertCompilingScalaSources(messages: Seq[CompilerMessage], number: Int): Unit = {
    val message = messages.find { message =>
      val text = message.getMessage
      text.contains("compiling") && text.contains("Scala source")
    }.orNull
    assertNotNull("Could not find Compiling Scala sources message", message)
    val expected = s"compiling $number Scala source"
    val text = message.getMessage
    assertTrue(s"Compiling wrong number of Scala sources, expected '$expected', got '$text'", text.contains(expected))
  }
}
