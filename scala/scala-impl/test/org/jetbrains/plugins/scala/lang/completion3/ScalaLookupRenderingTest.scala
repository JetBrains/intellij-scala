package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.codeInsight.lookup.LookupElement
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase.hasItemText
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestFixture.lookupItemsDebugText
import org.jetbrains.plugins.scala.util.ConfigureJavaFile.configureJavaFile
import org.junit.Assert.assertTrue
import org.junit.Test

class ScalaLookupRenderingTest extends ScalaCompletionTestBase {
  private def doTest(fileText: String, itemFilter: LookupElement => Boolean): Unit = {
    val (_, items) = activeLookupWithItems(fileText)

    assertTrue(
      s"""Lookup not found.
         |All lookups:
         |${lookupItemsDebugText(items)}""".stripMargin,
      items.exists(itemFilter)
    )
  }

  @Test
  def testJavaVarargs(): Unit = {
    configureJavaFile(
      fileText =
        """package a;
          |
          |public class Java {
          |  public static void foo(int... x) {}
          |}""".stripMargin,
      className = "Java",
      packageName = "a"
    )

    doTest(
      fileText =
        s"""import a.Java
           |class A {
           |  Java.fo$CARET
           |}""".stripMargin,
      itemFilter = hasItemText(_, "foo")(
        itemTextBold = true,
        tailText = "(x: Int*)",
        typeText = "Unit"
      )
    )
  }

  @Test
  def testParamWithAnnotation(): Unit = {
    doTest(
      fileText =
        s"""package tests
           |
           |object Declarations {
           |  def foo(@Deprecated("Since forever") param: String): Unit = ???
           |}
           |
           |object Test {
           |  Declarations.fo$CARET
           |}
           |""".stripMargin,
      itemFilter = hasItemText(_, "foo")(
        itemTextBold = true,
        tailText = "(param: String)",
        typeText = "Unit"
      )
    )
  }

  @Test
  def testJavaParamWithAnnotation(): Unit = {
    configureJavaFile(
      fileText =
        """package a;
          |
          |class Declarations {
          |    public static void foo(@Deprecated(since = "Since forever") String[] args) {
          |    }
          |}
          |""".stripMargin,
      className = "Java",
      packageName = "a"
    )

    doTest(
      fileText =
        s"""package tests
           |
           |import a.Declarations
           |
           |object Test {
           |  Declarations.fo$CARET
           |}
           |""".stripMargin,
      itemFilter = hasItemText(_, "foo")(
        itemTextBold = true,
        tailText = "(args: Array[String])",
        typeText = "Unit"
      )
    )
  }

  @Test
  def testInterleavedTypeClausesInLookupTailText(): Unit = {
    if (!version.isScala3) return

    doTest(
      fileText =
        s"""object Declarations:
           |  def foo[T](x: T)[U](u: U): U = u
           |
           |object Test:
           |  Declarations.fo$CARET
           |""".stripMargin,
      itemFilter = hasItemText(_, "foo")(
        itemTextBold = true,
        tailText = "[T](x: T)[U](u: U)",
        typeText = "U"
      )
    )
  }

  @Test
  def testMultipleInterleavedTypeClausesInLookupTailText(): Unit = {
    if (!version.isScala3) return

    doTest(
      fileText =
        s"""object Declarations:
           |  def foo[A](a: A)[B](b: B)[C](c: C): C = c
           |
           |object Test:
           |  Declarations.fo$CARET
           |""".stripMargin,
      itemFilter = hasItemText(_, "foo")(
        itemTextBold = true,
        tailText = "[A](a: A)[B](b: B)[C](c: C)",
        typeText = "C"
      )
    )
  }

  @Test
  def testInterleavedTypeClauseAfterUsingClauseInLookupTailText(): Unit = {
    if (!version.isScala3) return

    doTest(
      fileText =
        s"""object Declarations:
           |  def foo(first: Int)(using Int)[A](second: A): A = second
           |
           |object Test:
           |  Declarations.fo$CARET
           |""".stripMargin,
      itemFilter = hasItemText(_, "foo")(
        itemTextBold = true,
        tailText = "(first: Int)(using Int)[A](second: A)",
        typeText = "A"
      )
    )
  }
}
