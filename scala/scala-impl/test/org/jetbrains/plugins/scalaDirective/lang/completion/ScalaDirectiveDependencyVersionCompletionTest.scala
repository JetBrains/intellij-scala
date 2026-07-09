package org.jetbrains.plugins.scalaDirective.lang.completion

import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase
import org.jetbrains.plugins.scala.packagesearch.util.DependencyUtil
import org.jetbrains.plugins.scala.util.runners.WithIndexingMode
import org.junit.Test

//noinspection ApiStatus
@WithIndexingMode(mode = IndexingMode.DUMB_EMPTY_INDEX)
final class ScalaDirectiveDependencyVersionCompletionTest extends ScalaCompletionTestBase {

  @Test
  def testVersionCompletion(): Unit = {
    DependencyUtil.updateMockVersionCompletionCache(("foo", "bar") -> Seq("1.2.3"))
    doCompletionTest(
      fileText = s"//> using dep foo:bar:$CARET",
      resultText = s"//> using dep foo:bar:1.2.3$CARET",
      item = "foo:bar:1.2.3"
    )
  }

  @Test
  def testVersionCompletion2(): Unit = {
    DependencyUtil.updateMockVersionCompletionCache(("foo", "bar") -> Seq("1.2.3"))
    doCompletionTest(
      fileText = s"//> using dep foo:bar:1.$CARET",
      resultText = s"//> using dep foo:bar:1.2.3$CARET",
      item = "foo:bar:1.2.3"
    )
  }

  @Test
  def testVersionCompletion_inBetween(): Unit = {
    DependencyUtil.updateMockVersionCompletionCache(("foo", "bar") -> Seq("1.2.3"))
    doCompletionTest(
      fileText = s"//> using dep foo:bar:1.${CARET}1.0",
      resultText = s"//> using dep foo:bar:1.2.3$CARET",
      item = "foo:bar:1.2.3"
    )
  }

  @Test
  def testNoCompletionForVersionWhenPrefixIsDifferent(): Unit = {
    DependencyUtil.updateMockVersionCompletionCache(("foo", "bar") -> Seq("1.2.3"))
    checkNoBasicCompletion(
      fileText = s"//> using dep foo:bar:2$CARET",
      item = "foo:bar:1.2.3"
    )
  }

  @Test
  def testNoCompletionForVersionWhenNoStableVersionsFound(): Unit = {
    DependencyUtil.updateMockVersionCompletionCache(("foo", "bar") -> Seq("1.2.3-RC1"))
    checkNoBasicCompletion(
      fileText = s"//> using dep foo:bar:1$CARET",
      item = "foo:bar:1.2.3-RC1"
    )
  }

  @Test
  def testVersionCompletionWithUnstableOnSecondInvocation(): Unit = {
    DependencyUtil.updateMockVersionCompletionCache(("foo", "bar") -> Seq("1.2.3-RC1"))
    doCompletionTest(
      fileText = s"//> using dep foo:bar:1.$CARET",
      resultText = s"//> using dep foo:bar:1.2.3-RC1$CARET",
      item = "foo:bar:1.2.3-RC1",
      invocationCount = 2
    )
  }

  @Test
  def testNoCompletionForVersionWhenNothingFound(): Unit = {
    DependencyUtil.updateMockVersionCompletionCache(("foo", "bar") -> Seq.empty)
    checkNoBasicCompletion(
      fileText = s"//> using dep foo:bar:2$CARET",
      item = "foo:bar:1.2.3-RC1"
    )
  }
}
