package org.jetbrains.plugins.scala.compiler.highlighting

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CompilerOptionsTest {

  @Test
  def fatalWarningsFlag(): Unit = {
    val scalacOptions = Seq("-Wunused:patvars,imports,privates", "-Wunused:locals,explicits", "-Xfatal-warnings", "-Wunused:implicits", "-Wnumeric-widen")
    val flag = CompilerOptions.containsFatalWarnings(scalacOptions)
    assertTrue(flag, "Fatal warnings flag should be enabled")
  }

  @Test
  def wErrorFlag(): Unit = {
    val scalacOptions = Seq("-Wunused:patvars,imports,privates", "-Wunused:locals,explicits", "-Werror", "-Wunused:implicits", "-Wnumeric-widen")
    val flag = CompilerOptions.containsFatalWarnings(scalacOptions)
    assertTrue(flag, "Fatal warnings flag should be enabled")
  }

  @Test
  def unusedImportsFlag1(): Unit = {
    val scalacOptions = Seq("-Wunused:patvars,imports,privates", "-Wunused:locals,explicits", "-Xfatal-warnings", "-Wunused:implicits", "-Wnumeric-widen")
    val flag = CompilerOptions.containsUnusedImports(scalacOptions)
    assertTrue(flag, "Unused imports flag should be enabled")
  }

  @Test
  def unusedImportsFlag2(): Unit = {
    val scalacOptions = Seq("-Wunused:patvars,imports,privates", "-Wunused:locals,explicits", "-Xfatal-warnings", "-Wunused:implicits", "-Wnumeric-widen")
    val flag = CompilerOptions.containsUnusedImports(scalacOptions)
    assertTrue(flag, "Unused imports flag should be enabled")
  }

  @Test
  def unusedImportsFlag3(): Unit = {
    val scalacOptions = Seq("-Wnumeric-widen", "-Wunused:all", "-Werror")
    val flag = CompilerOptions.containsUnusedImports(scalacOptions)
    assertTrue(flag, "Unused imports flag should be enabled")
  }

  @Test
  def unusedImportsFlag4(): Unit = {
    val scalacOptions = Seq("-Wunused:all,privates,imports,explicits", "-Werror")
    val flag = CompilerOptions.containsUnusedImports(scalacOptions)
    assertTrue(flag, "Unused imports flag should be enabled")
  }
}
