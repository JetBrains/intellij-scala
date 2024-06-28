package org.jetbrains.plugins.scala.caches

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.Tracing.{Parameters, tracing}
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.extensions.inWriteCommandAction
import org.junit.Assert._

class CachingTest extends ScalaFixtureTestCase {
  override protected def supportedIn(version: ScalaVersion) = version == ScalaVersion.default

  def testScl22487(): Unit = {
    open("class Foo { type T = Foo. }")
    highlight()

    val trace = tracing(myFixture.getProject, Parameters(resolve = true, coalesce = true)) {
      replace(".", "")
      highlight()
    }

    assertEquals("Resolve: Foo → Foo", trace)
  }

  def testScl22704(): Unit = {
    open("class Foo { def foo(): Unit = { Seq(1).map { 2 }; () } }")
    highlight()

    val trace = tracing(myFixture.getProject, Parameters(resolve = true, coalesce = true, filter = "map")) {
      replace("2", "x => x")
      highlight()
    }

    assertEquals("Resolve: Seq(1).map → scala.collection.IterableOps.map", trace)
  }

  private def open(@Language("Scala") code: String): Any =
    myFixture.configureByText("Foo.scala", code)

  private def highlight(): Unit =
    myFixture.doHighlighting()

  private def replace(s1: String, s2: String): Unit = {
    val document = myFixture.getEditor.getDocument

    val i = document.getText.indexOf(s1)

    inWriteCommandAction {
      document.replaceString(i, i + s1.length, s2)
    }
  }
}
