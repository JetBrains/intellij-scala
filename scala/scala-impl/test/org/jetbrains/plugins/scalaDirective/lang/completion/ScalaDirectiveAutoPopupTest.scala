package org.jetbrains.plugins.scalaDirective.lang.completion

import com.intellij.codeInsight.editorActions.CompletionAutoPopupHandler
import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import com.intellij.testFramework.{TestModeFlags, UsefulTestCase}
import org.jetbrains.plugins.scala.base.ScalaCompletionAutoPopupTestCase
import org.jetbrains.plugins.scala.packagesearch.util.DependencyUtil
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsJUnit4Runner, RunWithScalaVersions, TestScalaVersion, WithIndexingMode}
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(classOf[MultipleScalaVersionsJUnit4Runner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_13,
  TestScalaVersion.Scala_3_Latest
))
@WithIndexingMode(mode = IndexingMode.DUMB_EMPTY_INDEX)
abstract class ScalaDirectiveAutoPopupTestBase extends ScalaCompletionAutoPopupTestCase {
  override def setUp(): Unit = {
    super.setUp()
    TestModeFlags.set[java.lang.Boolean](
      CompletionAutoPopupHandler.ourTestingAutopopup, true, getTestRootDisposable
    )
  }

  protected def doTest(textToType: String, expectedLookupItems: Seq[String])(src: String): Unit = {
    configureByText(src)
    doType(textToType)

    val actualLookupItems = myFixture.getLookupElementStrings

    UsefulTestCase.assertContainsElements[String](actualLookupItems, expectedLookupItems: _*)
  }

  protected def doTestNoAutoCompletion(textToType: String)(src: String): Unit = {
    configureByText(src)
    doType(textToType)

    assertNull("Lookup shouldn't be shown", getLookup)
  }
}

//noinspection ApiStatus
final class ScalaDirectiveAutoPopupTest extends ScalaDirectiveAutoPopupTestBase {

  @Test
  def testAutoPopupInScalaDirective(): Unit = doTest(">", UsingDirective :: Nil) {
    s"//$CARET"
  }

  @Test
  def testAutoPopupInScalaDirectiveWithSpacesBeforeComment(): Unit = doTest(">", UsingDirective :: Nil) {
    s"  //$CARET"
  }

  @Test
  def testAutoPopupInScalaDirectiveWithSpacesAfterCaret(): Unit = doTest(">", UsingDirective :: Nil) {
    s"""
       |//$CARET  ${""}
       |
       |object Foo
       |""".stripMargin
  }

  @Test
  def testNoAutoPopupInComment(): Unit = doTestNoAutoCompletion(">") {
    s"///$CARET"
  }

  @Test
  def testNoAutoPopupOnSpace(): Unit = doTestNoAutoCompletion(" ") {
    s"//>$CARET"
  }

  @Test
  def testAutoPopupInDependencyAfterArtifactId(): Unit = {
    DependencyUtil.updateMockVersionCompletionCache(("foo", "bar") -> Seq("1.2.3"))
    doTest(":", "foo:bar:1.2.3" :: Nil) {
      s"//> using dep foo:bar$CARET"
    }
  }

  @Test
  def testAutoPopupInDependencyKeyOnDot_test(): Unit = doTest(".", "test.dep" :: "test.deps" :: "test.dependencies" :: Nil) {
    s"//> using test$CARET"
  }

  @Test
  def testAutoPopupInDependencyKeyOnDot_compileOnly(): Unit = doTest(".", "compileOnly.dep" :: "compileOnly.deps" :: "compileOnly.dependencies" :: Nil) {
    s"//> using compileOnly$CARET"
  }

  @Test
  def testAutoPopupInDependencyKeyWithSelection_test(): Unit = doTest("te", "test.dep" :: "test.deps" :: "test.dependencies" :: Nil) {
    s"//> using ${START}dep$END foo:bar:1.2.3"
  }
}
