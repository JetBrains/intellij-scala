package org.jetbrains.plugins.scalaDirective.lang.completion

import com.intellij.codeInsight.editorActions.CompletionAutoPopupHandler
import com.intellij.testFramework.{TestModeFlags, UsefulTestCase}
import org.jetbrains.plugins.scala.base.ScalaCompletionAutoPopupTestCase
import org.jetbrains.plugins.scala.packagesearch.api.{PackageSearchClient, PackageSearchClientTesting}
import org.jetbrains.plugins.scala.packagesearch.util.DependencyUtil
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion}
import org.junit.Assert.assertNull
import org.junit.runner.RunWith

import java.util.Arrays.asList

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_13,
  TestScalaVersion.Scala_3_Latest
))
final class ScalaDirectiveAutoPopupTest
  extends ScalaCompletionAutoPopupTestCase
    with PackageSearchClientTesting {
  override def setUp(): Unit = {
    super.setUp()
    TestModeFlags.set[java.lang.Boolean](
      CompletionAutoPopupHandler.ourTestingAutopopup, true, getTestRootDisposable
    )
  }

  private def doTest(textToType: String, expectedLookupItems: Seq[String])(src: String): Unit = {
    configureByText(src)
    doType(textToType)

    val actualLookupItems = myFixture.getLookupElementStrings

    UsefulTestCase.assertContainsElements[String](actualLookupItems, expectedLookupItems: _*)
  }

  private def doTestNoAutoCompletion(textToType: String)(src: String): Unit = {
    configureByText(src)
    doType(textToType)

    assertNull("Lookup shouldn't be shown", getLookup)
  }

  def testAutoPopupInScalaDirective(): Unit = doTest(">", UsingDirective :: Nil) {
    s"//$CARET"
  }

  def testAutoPopupInScalaDirectiveWithSpacesBeforeComment(): Unit = doTest(">", UsingDirective :: Nil) {
    s"  //$CARET"
  }

  def testAutoPopupInScalaDirectiveWithSpacesAfterCaret(): Unit = doTest(">", UsingDirective :: Nil) {
    s"""
       |//$CARET  ${""}
       |
       |object Foo
       |""".stripMargin
  }

  def testNoAutoPopupInComment(): Unit = doTestNoAutoCompletion(">") {
    s"///$CARET"
  }

  def testNoAutoPopupOnSpace(): Unit = doTestNoAutoCompletion(" ") {
    s"//>$CARET"
  }

  def testAutoPopupInDependencyAfterGroupId(): Unit = {
    PackageSearchClient.instance()
      .updateByQueryCache("foo", "", asList(apiMavenPackage("foo", "bar", emptyVersionsContainer())))
    doTest(":", "foo:bar:" :: Nil) {
      s"//> using dep foo$CARET"
    }
  }

  def testAutoPopupInDependencyAfterArtifactId(): Unit = {
    DependencyUtil.updateMockVersionCompletionCache(("foo", "bar") -> Seq("1.2.3"))
    doTest(":", "foo:bar:1.2.3" :: Nil) {
      s"//> using dep foo:bar$CARET"
    }
  }

  def testNoAutoPopupInDependencyWithWrongKey(): Unit = {
    PackageSearchClient.instance()
      .updateByQueryCache("foo", "", asList(apiMavenPackage("foo", "bar", emptyVersionsContainer())))
    doTestNoAutoCompletion(":") {
      s"//> using something foo$CARET"
    }
  }

  def testAutoPopupInDependencyKeyOnDot_test(): Unit = doTest(".", "test.dep" :: "test.deps" :: "test.dependencies" :: Nil) {
    s"//> using test$CARET"
  }

  def testAutoPopupInDependencyKeyOnDot_compileOnly(): Unit = doTest(".", "compileOnly.dep" :: "compileOnly.deps" :: "compileOnly.dependencies" :: Nil) {
    s"//> using compileOnly$CARET"
  }

  def testAutoPopupInDependencyKeyWithSelection_test(): Unit = doTest("te", "test.dep" :: "test.deps" :: "test.dependencies" :: Nil) {
    s"//> using ${START}dep$END foo:bar:1.2.3"
  }
}
