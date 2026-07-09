package org.jetbrains.sbt.lang.completion

import org.jetbrains.plugins.scala.packagesearch.util.DependencyUtil
import org.jetbrains.plugins.scala.util.RevertableChange
import org.jetbrains.sbt.MockSbt_1_0
import org.junit.Test

//noinspection ApiStatus
abstract class SbtDependencyCompletionInsertHandlerTestBase
  extends SbtCompletionTestBase
    with MockSbt_1_0 {
  protected val GROUP_ID = "org.scalatest"
  protected val ARTIFACT_ID = "scalatest"
  protected val STABLE_VERSION = "3.0.8"
  protected val VERSIONS = Seq(STABLE_VERSION, "3.0.8-RC1", "3.0.8-RC2", "3.0.8-RC3", "3.0.8-RC4", "3.0.8-RC5")
  protected val LOOKUP_ITEM = s"$GROUP_ID:$ARTIFACT_ID"
  protected val RESULT_DEPENDENCY = s""""$GROUP_ID" % "$ARTIFACT_ID" % "$CARET""""

  protected def setupCaches(): Unit = {
    DependencyUtil.updateMockGroupIdCompletionCache(GROUP_ID)
    DependencyUtil.updateMockArtifactIdCompletionCache(GROUP_ID -> Seq(ARTIFACT_ID))

    DependencyUtil.updateMockVersionCompletionCache(
      (GROUP_ID, ARTIFACT_ID + "_2.13") -> VERSIONS,
      (GROUP_ID, ARTIFACT_ID + "_3") -> VERSIONS,
    )
  }

  protected def doTest(fileText: String, resultText: String, item: String, setupCaches: () => Unit = setupCaches): Unit = {
    setupCaches()

    // Tests with caret outside the string literal trigger completion that doesn't stop after adding dependencies
    // this is done so that we still have meaningful completions for things like `.intransitive()` on ModuleID
    // but it also means that there may be a lot of completion items which leads to nondeterministic test results
    RevertableChange.withModifiedRegistryValue("ide.completion.variant.limit", 1500).run {
      doCompletionTest(fileText = fileText, resultText = resultText, item = item)
    }
  }
}

final class SbtDependencyVersionsCompletionInsertHandlerTest extends SbtDependencyCompletionInsertHandlerTestBase {

  @Test
  def testTopLevel_Single_CompleteVersion_OutsideOfStringLiteral(): Unit = doTest(
    fileText =
      s"""
         |libraryDependencies += "$GROUP_ID" %% "$ARTIFACT_ID" % $CARET
         |""".stripMargin,
    resultText =
      s"""
         |libraryDependencies += "$GROUP_ID" %% "$ARTIFACT_ID" % "$STABLE_VERSION$CARET"
         |""".stripMargin,
    item = STABLE_VERSION
  )

  @Test
  def testTopLevel_Single_CompleteVersion_OutsideOfStringLiteral_WithOrgRef(): Unit = doTest(
    fileText =
      s"""
         |val org = "$GROUP_ID"
         |
         |libraryDependencies += org %% "$ARTIFACT_ID" % $CARET
         |""".stripMargin,
    resultText =
      s"""
         |val org = "$GROUP_ID"
         |
         |libraryDependencies += org %% "$ARTIFACT_ID" % "$STABLE_VERSION$CARET"
         |""".stripMargin,
    item = STABLE_VERSION
  )

  @Test
  def testTopLevel_Single_CompleteVersion_OutsideOfStringLiteral_WithArtifactRef(): Unit = doTest(
    fileText =
      s"""
         |val artifact = "$ARTIFACT_ID"
         |
         |libraryDependencies += "$GROUP_ID" %% artifact % $CARET
         |""".stripMargin,
    resultText =
      s"""
         |val artifact = "$ARTIFACT_ID"
         |
         |libraryDependencies += "$GROUP_ID" %% artifact % "$STABLE_VERSION$CARET"
         |""".stripMargin,
    item = STABLE_VERSION
  )

  @Test
  def testTopLevel_Single_CompleteVersion_OutsideOfStringLiteral_WithOrgRefAndArtifactRef(): Unit = doTest(
    fileText =
      s"""
         |val org = "$GROUP_ID"
         |val artifact = "$ARTIFACT_ID"
         |
         |libraryDependencies += org %% artifact % $CARET
         |""".stripMargin,
    resultText =
      s"""
         |val org = "$GROUP_ID"
         |val artifact = "$ARTIFACT_ID"
         |
         |libraryDependencies += org %% artifact % "$STABLE_VERSION$CARET"
         |""".stripMargin,
    item = STABLE_VERSION
  )

  @Test
  def testTopLevel_Single_CompleteVersion_OutsideOfStringLiteral_WithOrgAndArtifactRef(): Unit = doTest(
    fileText =
      s"""
         |val orgAndArtifact = "$GROUP_ID" %% "$ARTIFACT_ID"
         |
         |libraryDependencies += orgAndArtifact % $CARET
         |""".stripMargin,
    resultText =
      s"""
         |val orgAndArtifact = "$GROUP_ID" %% "$ARTIFACT_ID"
         |
         |libraryDependencies += orgAndArtifact % "$STABLE_VERSION$CARET"
         |""".stripMargin,
    item = STABLE_VERSION
  )
}
