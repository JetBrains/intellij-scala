package org.jetbrains.plugins.scala.reposearch.scalaDirective

import org.jetbrains.plugins.scala.packagesearch.util.DependencyUtil
import org.jetbrains.plugins.scalaDirective.lang.completion.ScalaDirectiveAutoPopupTestBase
import org.junit.Test

//noinspection ApiStatus
final class ScalaDirectiveDependencyAutoPopupTest extends ScalaDirectiveAutoPopupTestBase {
  @Test
  def testAutoPopupInDependencyAfterGroupId(): Unit = {
    DependencyUtil.updateMockArtifactIdCompletionCache("foo" -> Seq("bar"))
    doTest(":", "foo:bar:" :: Nil) {
      s"//> using dep foo$CARET"
    }
  }

  @Test
  def testNoAutoPopupInDependencyWithWrongKey(): Unit = {
    DependencyUtil.updateMockArtifactIdCompletionCache("foo" -> Seq("bar"))
    doTestNoAutoCompletion(":") {
      s"//> using something foo$CARET"
    }
  }
}
