package org.jetbrains.plugins.scalaDirective.lang.completion

import com.intellij.codeInsight.editorActions.CompletionAutoPopupHandler
import com.intellij.testFramework.{TestModeFlags, UsefulTestCase}
import org.jetbrains.plugins.scala.base.ScalaCompletionAutoPopupTestCase
import org.jetbrains.plugins.scala.packagesearch.api.PackageSearchApiClient
import org.jetbrains.plugins.scala.packagesearch.model.ApiPackage
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion}
import org.junit.Assert.assertNull
import org.junit.runner.RunWith

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_13,
  TestScalaVersion.Scala_3_Latest
))
final class ScalaDirectiveAutoPopupTest extends ScalaCompletionAutoPopupTestCase {
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
    PackageSearchApiClient.updateByQueryCache("foo", "", Seq(ApiPackage("foo", "bar", Seq())))
    doTest(":", "foo:bar:" :: Nil) {
      s"//> using dep foo$CARET"
    }
  }

  def testAutoPopupInDependencyAfterArtifactId(): Unit = {
    PackageSearchApiClient.updateByIdCache("foo", "bar", Some(ApiPackage("foo", "bar", Seq("1.2.3"))))
    doTest(":", "foo:bar:1.2.3" :: Nil) {
      s"//> using dep foo:bar$CARET"
    }
  }

  def testNoAutoPopupInDependencyWithWrongKey(): Unit = {
    PackageSearchApiClient.updateByQueryCache("foo", "", Seq(ApiPackage("foo", "bar", Seq())))
    doTestNoAutoCompletion(":") {
      s"//> using something foo$CARET"
    }
  }
}
