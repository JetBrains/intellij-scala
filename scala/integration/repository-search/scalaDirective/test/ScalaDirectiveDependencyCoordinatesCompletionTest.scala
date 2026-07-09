//noinspection ApiStatus
package org.jetbrains.plugins.scala.reposearch.scalaDirective

import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase
import org.jetbrains.plugins.scala.packagesearch.util.DependencyUtil
import org.jetbrains.plugins.scala.util.runners.{RunWithScalaVersions, TestScalaVersion, WithIndexingMode}
import org.junit.Assert.assertTrue
import org.junit.Test

@WithIndexingMode(mode = IndexingMode.DUMB_EMPTY_INDEX)
abstract class ScalaDirectiveDependencyCoordinatesCompletionTestBase extends ScalaCompletionTestBase {
  @Test
  def testGroupIdPosition(): Unit = {
    DependencyUtil.updateMockGroupIdCompletionCache("foo")
    DependencyUtil.updateMockArtifactIdCompletionCache("foo" -> Seq("bar"))
    doCompletionTest(
      fileText = s"//> using dep fo$CARET",
      resultText = s"//> using dep foo:bar:$CARET",
      item = "foo:bar:"
    )
  }

  @Test
  def testGroupIdPosition_inBetween(): Unit = {
    DependencyUtil.updateMockGroupIdCompletionCache("foo")
    DependencyUtil.updateMockArtifactIdCompletionCache("foo" -> Seq("bar"))
    doCompletionTest(
      fileText = s"//> using dep fo${CARET}something",
      resultText = s"//> using dep foo:bar:$CARET",
      item = "foo:bar:"
    )
  }

  @Test
  def testGroupIdPosition_inBetween2(): Unit = {
    DependencyUtil.updateMockGroupIdCompletionCache("foo")
    DependencyUtil.updateMockArtifactIdCompletionCache("foo" -> Seq("bar"))
    doCompletionTest(
      fileText = s"//> using dep fo${CARET}something:another-artifact",
      resultText = s"//> using dep foo:bar:$CARET",
      item = "foo:bar:"
    )
  }

  @Test
  def testArtifactIdPosition(): Unit = {
    DependencyUtil.updateMockArtifactIdCompletionCache("foo" -> Seq("bar"))
    doCompletionTest(
      fileText = s"//> using dep foo:b$CARET",
      resultText = s"//> using dep foo:bar:$CARET",
      item = "foo:bar:"
    )
  }

  @Test
  def testArtifactIdPosition_inBetween(): Unit = {
    DependencyUtil.updateMockArtifactIdCompletionCache("foo" -> Seq("bar"))
    doCompletionTest(
      fileText = s"//> using dep foo:b${CARET}oo",
      resultText = s"//> using dep foo:bar:$CARET",
      item = "foo:bar:"
    )
  }

  @Test
  def testArtifactIdPosition_inBetween2(): Unit = {
    DependencyUtil.updateMockArtifactIdCompletionCache("foo" -> Seq("bar"))
    doCompletionTest(
      fileText = s"//> using dep foo:b${CARET}oo:1.2.3",
      resultText = s"//> using dep foo:bar:$CARET",
      item = "foo:bar:"
    )
  }

  @Test
  def testNoCompletionWhenSelectedCrossVersionButHaveOnlyRegularArtifact(): Unit = {
    DependencyUtil.updateMockArtifactIdCompletionCache("foo" -> Seq("bar"))
    checkNoBasicCompletion(fileText = s"//> using dep foo::b${CARET}oo", item = "foo:bar:")
  }
}

@RunWithScalaVersions(Array(TestScalaVersion.Scala_2_13))
final class ScalaDirectiveDependencyCoordinatesCompletionTest_Scala2_13 extends ScalaDirectiveDependencyCoordinatesCompletionTestBase {
  @Test
  def testGroupIdPosition_crossVersion(): Unit = {
    DependencyUtil.updateMockGroupIdCompletionCache("foo")
    DependencyUtil.updateMockArtifactIdCompletionCache("foo" -> Seq("bar_2.13"))
    doCompletionTest(
      fileText = s"//> using dep fo$CARET",
      resultText = s"//> using dep foo::bar:$CARET",
      item = "foo::bar:"
    )
  }

  @Test
  def testGroupIdPosition_incompatibleCrossVersion_noCompletion(): Unit = {
    DependencyUtil.updateMockGroupIdCompletionCache("foo")
    DependencyUtil.updateMockArtifactIdCompletionCache("foo" -> Seq("bar_2.12"))
    checkNoBasicCompletion(
      fileText = s"//> using dep fo$CARET",
      item = "foo::bar:"
    )
  }

  @Test
  def testGroupIdPosition_incompatibleCrossVersion_noCompletion2(): Unit = {
    DependencyUtil.updateMockGroupIdCompletionCache("foo")
    DependencyUtil.updateMockArtifactIdCompletionCache("foo" -> Seq("bar_3"))
    checkNoBasicCompletion(
      fileText = s"//> using dep fo$CARET",
      item = "foo::bar:"
    )
  }

  @Test
  def testGroupIdPosition_fullCrossVersion(): Unit = {
    val projectScalaVersion = version.minor
    assertTrue("Expected the test to run with Scala 2.13", projectScalaVersion.startsWith("2.13."))
    DependencyUtil.updateMockGroupIdCompletionCache("foo")
    DependencyUtil.updateMockArtifactIdCompletionCache("foo" -> Seq(s"bar_$projectScalaVersion"))
    doCompletionTest(
      fileText = s"//> using dep fo$CARET",
      resultText = s"//> using dep foo:::bar:$CARET",
      item = "foo:::bar:"
    )
  }

  @Test
  def testGroupIdPosition_fullIncompatibleCrossVersion_noCompletion(): Unit = {
    DependencyUtil.updateMockGroupIdCompletionCache("foo")
    DependencyUtil.updateMockArtifactIdCompletionCache("foo" -> Seq("bar_3.3.0"))
    checkNoBasicCompletion(
      fileText = s"//> using dep fo$CARET",
      item = "foo:::bar:"
    )
  }

  @Test
  def testArtifactIdPosition_crossVersion(): Unit = {
    DependencyUtil.updateMockArtifactIdCompletionCache("foo" -> Seq("bar_2.13"))
    doCompletionTest(
      fileText = s"//> using dep foo:b$CARET",
      resultText = s"//> using dep foo::bar:$CARET",
      item = "foo::bar:"
    )
  }

  @Test
  def testArtifactIdPosition_incompatibleCrossVersion_noCompletion(): Unit = {
    DependencyUtil.updateMockArtifactIdCompletionCache("foo" -> Seq("bar_3"))
    checkNoBasicCompletion(
      fileText = s"//> using dep foo::b$CARET",
      item = "foo::bar:"
    )
  }

  @Test
  def testArtifactIdPosition_fullCrossVersion(): Unit = {
    val projectScalaVersion = version.minor
    assertTrue("Expected the test to run with Scala 2.13", projectScalaVersion.startsWith("2.13."))
    DependencyUtil.updateMockArtifactIdCompletionCache("foo" -> Seq(s"bar_$projectScalaVersion"))
    doCompletionTest(
      fileText = s"//> using dep foo:b$CARET",
      resultText = s"//> using dep foo:::bar:$CARET",
      item = "foo:::bar:"
    )
  }

  @Test
  def testArtifactIdPosition_fullIncompatibleCrossVersion_noCompletion(): Unit = {
    DependencyUtil.updateMockArtifactIdCompletionCache("foo" -> Seq("bar_3.3.0"))
    checkNoBasicCompletion(
      fileText = s"//> using dep foo:b$CARET",
      item = "foo:::bar:"
    )
  }
}

@RunWithScalaVersions(Array(TestScalaVersion.Scala_3_Latest))
final class ScalaDirectiveDependencyCoordinatesCompletionTest_Scala3 extends ScalaDirectiveDependencyCoordinatesCompletionTestBase {
  @Test
  def testGroupIdPosition_crossVersion(): Unit = {
    DependencyUtil.updateMockGroupIdCompletionCache("foo")
    DependencyUtil.updateMockArtifactIdCompletionCache("foo" -> Seq("bar_3"))
    doCompletionTest(
      fileText = s"//> using dep fo$CARET",
      resultText = s"//> using dep foo::bar:$CARET",
      item = "foo::bar:"
    )
  }

  @Test
  def testGroupIdPosition_incompatibleCrossVersion_noCompletion(): Unit = {
    DependencyUtil.updateMockGroupIdCompletionCache("foo")
    DependencyUtil.updateMockArtifactIdCompletionCache("foo" -> Seq("bar_2.12"))
    checkNoBasicCompletion(
      fileText = s"//> using dep fo$CARET",
      item = "foo::bar:"
    )
  }

  @Test
  def testGroupIdPosition_incompatibleCrossVersion_noCompletion2(): Unit = {
    DependencyUtil.updateMockGroupIdCompletionCache("foo")
    DependencyUtil.updateMockArtifactIdCompletionCache("foo" -> Seq("bar_2.13"))
    checkNoBasicCompletion(
      fileText = s"//> using dep fo$CARET",
      item = "foo::bar:"
    )
  }

  @Test
  def testGroupIdPosition_fullCrossVersion(): Unit = {
    val projectScalaVersion = version.minor
    assertTrue("Expected the test to run with Scala 3", projectScalaVersion.startsWith("3."))
    DependencyUtil.updateMockGroupIdCompletionCache("foo")
    DependencyUtil.updateMockArtifactIdCompletionCache("foo" -> Seq(s"bar_${version.minor}"))
    doCompletionTest(
      fileText = s"//> using dep fo$CARET",
      resultText = s"//> using dep foo:::bar:$CARET",
      item = "foo:::bar:"
    )
  }

  @Test
  def testGroupIdPosition_fullIncompatibleCrossVersion_noCompletion(): Unit = {
    DependencyUtil.updateMockGroupIdCompletionCache("foo")
    DependencyUtil.updateMockArtifactIdCompletionCache("foo" -> Seq("bar_2.12.10"))
    checkNoBasicCompletion(
      fileText = s"//> using dep fo$CARET",
      item = "foo:::bar:"
    )
  }

  @Test
  def testArtifactIdPosition_crossVersion(): Unit = {
    DependencyUtil.updateMockArtifactIdCompletionCache("foo" -> Seq("bar_3"))
    doCompletionTest(
      fileText = s"//> using dep foo:b$CARET",
      resultText = s"//> using dep foo::bar:$CARET",
      item = "foo::bar:"
    )
  }

  @Test
  def testArtifactIdPosition_incompatibleCrossVersion_noCompletion(): Unit = {
    DependencyUtil.updateMockArtifactIdCompletionCache("foo" -> Seq("bar_2.13"))
    checkNoBasicCompletion(
      fileText = s"//> using dep foo::b$CARET",
      item = "foo::bar:"
    )
  }

  @Test
  def testArtifactIdPosition_fullCrossVersion(): Unit = {
    val projectScalaVersion = version.minor
    assertTrue("Expected the test to run with Scala 3", projectScalaVersion.startsWith("3."))
    DependencyUtil.updateMockArtifactIdCompletionCache("foo" -> Seq(s"bar_$projectScalaVersion"))
    doCompletionTest(
      fileText = s"//> using dep foo:b$CARET",
      resultText = s"//> using dep foo:::bar:$CARET",
      item = "foo:::bar:"
    )
  }

  @Test
  def testArtifactIdPosition_fullIncompatibleCrossVersion_noCompletion(): Unit = {
    DependencyUtil.updateMockArtifactIdCompletionCache("foo" -> Seq("bar_2.12.10"))
    checkNoBasicCompletion(
      fileText = s"//> using dep foo:b$CARET",
      item = "foo:::bar:"
    )
  }
}
