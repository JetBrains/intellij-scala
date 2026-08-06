package org.jetbrains.sbt.lang.completion

import org.jetbrains.plugins.scala.packagesearch.api.PackageSearchClientTesting
import org.jetbrains.plugins.scala.packagesearch.util.DependencyUtil
import org.jetbrains.plugins.scala.util.RevertableChange
import org.jetbrains.sbt.MockSbt_1_0
import org.junit.Test

//noinspection ApiStatus
class SbtDependenciesCompletionInsertHandlerTest
  extends SbtCompletionTestBase
    with PackageSearchClientTesting
    with MockSbt_1_0 {
  private val GROUP_ID = "org.scalatest"
  private val ARTIFACT_ID = "scalatest"
  private val STABLE_VERSION = "3.0.8"
  private val VERSIONS = Seq(STABLE_VERSION, "3.0.8-RC1", "3.0.8-RC2", "3.0.8-RC3", "3.0.8-RC4", "3.0.8-RC5")
  private val LOOKUP_ITEM = s"$GROUP_ID:$ARTIFACT_ID"
  private val RESULT_DEPENDENCY = s""""$GROUP_ID" % "$ARTIFACT_ID" % "$CARET""""

  private def setupCaches(): Unit = {
    // TODO: SCL-23246 Reimplement using new maven search api.
    //       Check the remaining code in <community>/scala/package-search-client.
//    val packages = java.util.Arrays.asList(apiMavenPackage(GROUP_ID, ARTIFACT_ID, versionsContainer(STABLE_VERSION, VERSIONS)))
//    PackageSearchClient.instance().updateByQueryCache("", "", packages)
//    PackageSearchClient.instance().updateByQueryCache(GROUP_ID, "", packages)
//    PackageSearchClient.instance().updateByQueryCache("sca", "", packages)

    DependencyUtil.updateMockVersionCompletionCache(
      (GROUP_ID, ARTIFACT_ID + "_2.13") -> VERSIONS,
      (GROUP_ID, ARTIFACT_ID + "_3") -> VERSIONS,
    )
  }

  private def doTest(fileText: String, resultText: String, item: String): Unit = {
    setupCaches()

    // Tests with caret outside the string literal trigger completion that doesn't stop after adding dependencies
    // this is done so that we still have meaningful completions for things like `.intransitive()` on ModuleID
    // but it also means that there may be a lot of completion items which leads to nondeterministic test results
    RevertableChange.withModifiedRegistryValue("ide.completion.variant.limit", 1500).run {
      doCompletionTest(fileText = fileText, resultText = resultText, item = item)
    }
  }

  @Test
  def testTopLevel_CompletionDoesNotStopOutsideStringLiteral(): Unit = doTest(
    fileText =
      s"""
         |libraryDependencies += ("$GROUP_ID" %% "$ARTIFACT_ID" % "$STABLE_VERSION").in$CARET
         |""".stripMargin,
    resultText =
      s"""
         |libraryDependencies += ("$GROUP_ID" %% "$ARTIFACT_ID" % "$STABLE_VERSION").intransitive()$CARET
         |""".stripMargin,
    item = "intransitive"
  )

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

//  @Test
//  def testTopLevel_Single_OutsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |libraryDependencies += $CARET
//         |""".stripMargin,
//    resultText =
//      s"""
//         |libraryDependencies += $RESULT_DEPENDENCY
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testTopLevel_Single_InsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |libraryDependencies += "$CARET"
//         |""".stripMargin,
//    resultText =
//      s"""
//         |libraryDependencies += $RESULT_DEPENDENCY
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testTopLevel_Single_CompleteArtifact_OutsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |libraryDependencies += "$GROUP_ID" % $CARET
//         |""".stripMargin,
//    resultText =
//      s"""
//         |libraryDependencies += $RESULT_DEPENDENCY
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testTopLevel_Single_CompleteArtifact_InsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |libraryDependencies += "$GROUP_ID" % "$CARET"
//         |""".stripMargin,
//    resultText =
//      s"""
//         |libraryDependencies += $RESULT_DEPENDENCY
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testTopLevel_Single_CompleteArtifactWithDefinedVersion_OutsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |libraryDependencies += "$GROUP_ID" % $CARET % "0.0.1"
//         |""".stripMargin,
//    resultText =
//      s"""
//         |libraryDependencies += "$GROUP_ID" % "$ARTIFACT_ID$CARET" % "0.0.1"
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testTopLevel_Single_CompleteArtifactWithDefinedVersion_InsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |libraryDependencies += "$GROUP_ID" % "$CARET" % "0.0.1"
//         |""".stripMargin,
//    resultText =
//      s"""
//         |libraryDependencies += "$GROUP_ID" % "$ARTIFACT_ID$CARET" % "0.0.1"
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testTopLevel_SeqOneLine_OutsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |libraryDependencies ++= Seq($CARET)
//         |""".stripMargin,
//    resultText =
//      s"""
//         |libraryDependencies ++= Seq($RESULT_DEPENDENCY)
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testTopLevel_SeqOneLine_InsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |libraryDependencies ++= Seq("$CARET")
//         |""".stripMargin,
//    resultText =
//      s"""
//         |libraryDependencies ++= Seq($RESULT_DEPENDENCY)
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

  //region completion inside Seq inheritors
//  @Test
//  def testTopLevel_ListOneLine_OutsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |libraryDependencies ++= List($CARET)
//         |""".stripMargin,
//    resultText =
//      s"""
//         |libraryDependencies ++= List($RESULT_DEPENDENCY)
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testTopLevel_ListOneLine_InsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |libraryDependencies ++= List("$CARET")
//         |""".stripMargin,
//    resultText =
//      s"""
//         |libraryDependencies ++= List($RESULT_DEPENDENCY)
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testTopLevel_VectorOneLine_OutsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |libraryDependencies ++= Vector($CARET)
//         |""".stripMargin,
//    resultText =
//      s"""
//         |libraryDependencies ++= Vector($RESULT_DEPENDENCY)
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testTopLevel_VectorOneLine_InsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |libraryDependencies ++= Vector("$CARET")
//         |""".stripMargin,
//    resultText =
//      s"""
//         |libraryDependencies ++= Vector($RESULT_DEPENDENCY)
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )
  //endregion

//  @Test
//  def testTopLevel_SeqOneLine_CompleteArtifact_OutsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |libraryDependencies ++= Seq("$GROUP_ID" % $CARET)
//         |""".stripMargin,
//    resultText =
//      s"""
//         |libraryDependencies ++= Seq($RESULT_DEPENDENCY)
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testTopLevel_SeqOneLine_CompleteArtifact_InsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |libraryDependencies ++= Seq("$GROUP_ID" % "$CARET")
//         |""".stripMargin,
//    resultText =
//      s"""
//         |libraryDependencies ++= Seq($RESULT_DEPENDENCY)
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testTopLevel_SeqOneLine_CompleteArtifact_InsideOfMultilineStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |libraryDependencies ++= Seq("$GROUP_ID" % ""\"$CARET""\")
//         |""".stripMargin,
//    resultText =
//      s"""
//         |libraryDependencies ++= Seq($RESULT_DEPENDENCY)
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testTopLevel_SeqOneLine_CompleteArtifactWithDefinedVersion_OutsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |libraryDependencies ++= Seq("$GROUP_ID" % $CARET % "0.0.1")
//         |""".stripMargin,
//    resultText =
//      s"""
//         |libraryDependencies ++= Seq("$GROUP_ID" % "$ARTIFACT_ID$CARET" % "0.0.1")
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testTopLevel_SeqOneLine_CompleteArtifactWithDefinedVersion_InsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |libraryDependencies ++= Seq("$GROUP_ID" % "$CARET" % "0.0.1")
//         |""".stripMargin,
//    resultText =
//      s"""
//         |libraryDependencies ++= Seq("$GROUP_ID" % "$ARTIFACT_ID$CARET" % "0.0.1")
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testTopLevel_SeqMultilineFirst_OutsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |libraryDependencies ++= Seq(
//         |  $CARET
//         |)
//         |""".stripMargin,
//    resultText =
//      s"""
//         |libraryDependencies ++= Seq(
//         |  $RESULT_DEPENDENCY
//         |)
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testTopLevel_SeqMultilineFirst_InsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |libraryDependencies ++= Seq(
//         |  "$CARET"
//         |)
//         |""".stripMargin,
//    resultText =
//      s"""
//         |libraryDependencies ++= Seq(
//         |  $RESULT_DEPENDENCY
//         |)
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testTopLevel_SeqMultilineFirst_CompleteArtifact_OutsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |libraryDependencies ++= Seq(
//         |  "$GROUP_ID" % $CARET
//         |)
//         |""".stripMargin,
//    resultText =
//      s"""
//         |libraryDependencies ++= Seq(
//         |  $RESULT_DEPENDENCY
//         |)
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testTopLevel_SeqMultilineFirst_CompleteArtifact_InsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |libraryDependencies ++= Seq(
//         |  "$GROUP_ID" % "$CARET"
//         |)
//         |""".stripMargin,
//    resultText =
//      s"""
//         |libraryDependencies ++= Seq(
//         |  $RESULT_DEPENDENCY
//         |)
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testTopLevel_SeqMultilineFirst_CompleteArtifactWithDefinedVersion_OutsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |libraryDependencies ++= Seq(
//         |  "$GROUP_ID" % $CARET % "0.0.1"
//         |)
//         |""".stripMargin,
//    resultText =
//      s"""
//         |libraryDependencies ++= Seq(
//         |  "$GROUP_ID" % "$ARTIFACT_ID$CARET" % "0.0.1"
//         |)
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testTopLevel_SeqMultilineFirst_CompleteArtifactWithDefinedVersion_InsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |libraryDependencies ++= Seq(
//         |  "$GROUP_ID" % "$CARET" % "0.0.1"
//         |)
//         |""".stripMargin,
//    resultText =
//      s"""
//         |libraryDependencies ++= Seq(
//         |  "$GROUP_ID" % "$ARTIFACT_ID$CARET" % "0.0.1"
//         |)
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testTopLevel_SeqMultilineSecond_OutsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |libraryDependencies ++= Seq(
//         |  "foo" % "bar" % "baz",
//         |  $CARET
//         |)
//         |""".stripMargin,
//    resultText =
//      s"""
//         |libraryDependencies ++= Seq(
//         |  "foo" % "bar" % "baz",
//         |  $RESULT_DEPENDENCY
//         |)
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testTopLevel_SeqMultilineSecond_InsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |libraryDependencies ++= Seq(
//         |  "foo" % "bar" % "baz",
//         |  "$CARET"
//         |)
//         |""".stripMargin,
//    resultText =
//      s"""
//         |libraryDependencies ++= Seq(
//         |  "foo" % "bar" % "baz",
//         |  $RESULT_DEPENDENCY
//         |)
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testTopLevel_SeqMultilineSecond_CompleteArtifact_OutsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |libraryDependencies ++= Seq(
//         |  "foo" % "bar" % "baz",
//         |  "$GROUP_ID" % $CARET
//         |)
//         |""".stripMargin,
//    resultText =
//      s"""
//         |libraryDependencies ++= Seq(
//         |  "foo" % "bar" % "baz",
//         |  $RESULT_DEPENDENCY
//         |)
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testTopLevel_SeqMultilineSecond_CompleteArtifact_InsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |libraryDependencies ++= Seq(
//         |  "foo" % "bar" % "baz",
//         |  "$GROUP_ID" % "$CARET"
//         |)
//         |""".stripMargin,
//    resultText =
//      s"""
//         |libraryDependencies ++= Seq(
//         |  "foo" % "bar" % "baz",
//         |  $RESULT_DEPENDENCY
//         |)
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testTopLevel_SeqMultilineSecond_CompleteArtifactWithDefinedVersion_OutsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |libraryDependencies ++= Seq(
//         |  "foo" % "bar" % "baz",
//         |  "$GROUP_ID" % $CARET % "0.0.1"
//         |)
//         |""".stripMargin,
//    resultText =
//      s"""
//         |libraryDependencies ++= Seq(
//         |  "foo" % "bar" % "baz",
//         |  "$GROUP_ID" % "$ARTIFACT_ID$CARET" % "0.0.1"
//         |)
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testTopLevel_SeqMultilineSecond_CompleteArtifactWithDefinedVersion_InsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |libraryDependencies ++= Seq(
//         |  "foo" % "bar" % "baz",
//         |  "$GROUP_ID" % "$CARET" % "0.0.1"
//         |)
//         |""".stripMargin,
//    resultText =
//      s"""
//         |libraryDependencies ++= Seq(
//         |  "foo" % "bar" % "baz",
//         |  "$GROUP_ID" % "$ARTIFACT_ID$CARET" % "0.0.1"
//         |)
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testInProjectSettings_Single_OutsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |lazy val foo = project.in(file("foo"))
//         |  .settings(
//         |    name := "foo",
//         |    scalaVersion := "${version.minor}",
//         |    libraryDependencies += $CARET
//         |  )
//         |""".stripMargin,
//    resultText =
//      s"""
//         |lazy val foo = project.in(file("foo"))
//         |  .settings(
//         |    name := "foo",
//         |    scalaVersion := "${version.minor}",
//         |    libraryDependencies += $RESULT_DEPENDENCY
//         |  )
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testInProjectSettings_Single_InsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |lazy val foo = project.in(file("foo"))
//         |  .settings(
//         |    name := "foo",
//         |    scalaVersion := "${version.minor}",
//         |    libraryDependencies += "$CARET"
//         |  )
//         |""".stripMargin,
//    resultText =
//      s"""
//         |lazy val foo = project.in(file("foo"))
//         |  .settings(
//         |    name := "foo",
//         |    scalaVersion := "${version.minor}",
//         |    libraryDependencies += $RESULT_DEPENDENCY
//         |  )
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testInProjectSettings_Single_CompleteArtifact_OutsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |lazy val foo = project.in(file("foo"))
//         |  .settings(
//         |    name := "foo",
//         |    scalaVersion := "${version.minor}",
//         |    libraryDependencies += "$GROUP_ID" % $CARET
//         |  )
//         |""".stripMargin,
//    resultText =
//      s"""
//         |lazy val foo = project.in(file("foo"))
//         |  .settings(
//         |    name := "foo",
//         |    scalaVersion := "${version.minor}",
//         |    libraryDependencies += $RESULT_DEPENDENCY
//         |  )
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testInProjectSettings_Single_CompleteArtifact_InsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |lazy val foo = project.in(file("foo"))
//         |  .settings(
//         |    name := "foo",
//         |    scalaVersion := "${version.minor}",
//         |    libraryDependencies += "$GROUP_ID" % "$CARET"
//         |  )
//         |""".stripMargin,
//    resultText =
//      s"""
//         |lazy val foo = project.in(file("foo"))
//         |  .settings(
//         |    name := "foo",
//         |    scalaVersion := "${version.minor}",
//         |    libraryDependencies += $RESULT_DEPENDENCY
//         |  )
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testInProjectSettings_Single_CompleteArtifactWithDefinedVersion_OutsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |lazy val foo = project.in(file("foo"))
//         |  .settings(
//         |    name := "foo",
//         |    scalaVersion := "${version.minor}",
//         |    libraryDependencies += "$GROUP_ID" % $CARET % "0.0.1"
//         |  )
//         |""".stripMargin,
//    resultText =
//      s"""
//         |lazy val foo = project.in(file("foo"))
//         |  .settings(
//         |    name := "foo",
//         |    scalaVersion := "${version.minor}",
//         |    libraryDependencies += "$GROUP_ID" % "$ARTIFACT_ID$CARET" % "0.0.1"
//         |  )
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testInProjectSettings_Single_CompleteArtifactWithDefinedVersion_InsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |lazy val foo = project.in(file("foo"))
//         |  .settings(
//         |    name := "foo",
//         |    scalaVersion := "${version.minor}",
//         |    libraryDependencies += "$GROUP_ID" % "$CARET" % "0.0.1"
//         |  )
//         |""".stripMargin,
//    resultText =
//      s"""
//         |lazy val foo = project.in(file("foo"))
//         |  .settings(
//         |    name := "foo",
//         |    scalaVersion := "${version.minor}",
//         |    libraryDependencies += "$GROUP_ID" % "$ARTIFACT_ID$CARET" % "0.0.1"
//         |  )
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testInProjectSettings_SeqOneLine_OutsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |lazy val foo = project.in(file("foo"))
//         |  .settings(
//         |    name := "foo",
//         |    scalaVersion := "${version.minor}",
//         |    libraryDependencies ++= Seq($CARET)
//         |  )
//         |""".stripMargin,
//    resultText =
//      s"""
//         |lazy val foo = project.in(file("foo"))
//         |  .settings(
//         |    name := "foo",
//         |    scalaVersion := "${version.minor}",
//         |    libraryDependencies ++= Seq($RESULT_DEPENDENCY)
//         |  )
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testInProjectSettings_SeqOneLine_InsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |lazy val foo = project.in(file("foo"))
//         |  .settings(
//         |    name := "foo",
//         |    scalaVersion := "${version.minor}",
//         |    libraryDependencies ++= Seq("$CARET")
//         |  )
//         |""".stripMargin,
//    resultText =
//      s"""
//         |lazy val foo = project.in(file("foo"))
//         |  .settings(
//         |    name := "foo",
//         |    scalaVersion := "${version.minor}",
//         |    libraryDependencies ++= Seq($RESULT_DEPENDENCY)
//         |  )
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testInProjectSettings_SeqOneLine_CompleteArtifact_OutsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |lazy val foo = project.in(file("foo"))
//         |  .settings(
//         |    name := "foo",
//         |    scalaVersion := "${version.minor}",
//         |    libraryDependencies ++= Seq("$GROUP_ID" % $CARET)
//         |  )
//         |""".stripMargin,
//    resultText =
//      s"""
//         |lazy val foo = project.in(file("foo"))
//         |  .settings(
//         |    name := "foo",
//         |    scalaVersion := "${version.minor}",
//         |    libraryDependencies ++= Seq($RESULT_DEPENDENCY)
//         |  )
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testInProjectSettings_SeqOneLine_CompleteArtifact_InsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |lazy val foo = project.in(file("foo"))
//         |  .settings(
//         |    name := "foo",
//         |    scalaVersion := "${version.minor}",
//         |    libraryDependencies ++= Seq("$GROUP_ID" % "$CARET")
//         |  )
//         |""".stripMargin,
//    resultText =
//      s"""
//         |lazy val foo = project.in(file("foo"))
//         |  .settings(
//         |    name := "foo",
//         |    scalaVersion := "${version.minor}",
//         |    libraryDependencies ++= Seq($RESULT_DEPENDENCY)
//         |  )
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testInProjectSettings_SeqOneLine_CompleteArtifactWithDefinedVersion_OutsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |lazy val foo = project.in(file("foo"))
//         |  .settings(
//         |    name := "foo",
//         |    scalaVersion := "${version.minor}",
//         |    libraryDependencies ++= Seq("$GROUP_ID" % $CARET % "0.0.1")
//         |  )
//         |""".stripMargin,
//    resultText =
//      s"""
//         |lazy val foo = project.in(file("foo"))
//         |  .settings(
//         |    name := "foo",
//         |    scalaVersion := "${version.minor}",
//         |    libraryDependencies ++= Seq("$GROUP_ID" % "$ARTIFACT_ID$CARET" % "0.0.1")
//         |  )
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testInProjectSettings_SeqOneLine_CompleteArtifactWithDefinedVersion_InsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |lazy val foo = project.in(file("foo"))
//         |  .settings(
//         |    name := "foo",
//         |    scalaVersion := "${version.minor}",
//         |    libraryDependencies ++= Seq("$GROUP_ID" % "$CARET" % "0.0.1")
//         |  )
//         |""".stripMargin,
//    resultText =
//      s"""
//         |lazy val foo = project.in(file("foo"))
//         |  .settings(
//         |    name := "foo",
//         |    scalaVersion := "${version.minor}",
//         |    libraryDependencies ++= Seq("$GROUP_ID" % "$ARTIFACT_ID$CARET" % "0.0.1")
//         |  )
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testInProjectSettings_SeqMultilineFirst_OutsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |lazy val foo = project.in(file("foo"))
//         |  .settings(
//         |    name := "foo",
//         |    scalaVersion := "${version.minor}",
//         |    libraryDependencies ++= Seq(
//         |      $CARET
//         |    )
//         |  )
//         |""".stripMargin,
//    resultText =
//      s"""
//         |lazy val foo = project.in(file("foo"))
//         |  .settings(
//         |    name := "foo",
//         |    scalaVersion := "${version.minor}",
//         |    libraryDependencies ++= Seq(
//         |      $RESULT_DEPENDENCY
//         |    )
//         |  )
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testInProjectSettings_SeqMultilineFirst_InsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |lazy val foo = project.in(file("foo"))
//         |  .settings(
//         |    name := "foo",
//         |    scalaVersion := "${version.minor}",
//         |    libraryDependencies ++= Seq(
//         |      "$CARET"
//         |    )
//         |  )
//         |""".stripMargin,
//    resultText =
//      s"""
//         |lazy val foo = project.in(file("foo"))
//         |  .settings(
//         |    name := "foo",
//         |    scalaVersion := "${version.minor}",
//         |    libraryDependencies ++= Seq(
//         |      $RESULT_DEPENDENCY
//         |    )
//         |  )
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testInProjectSettings_SeqMultilineFirst_CompleteArtifact_OutsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |lazy val foo = project.in(file("foo"))
//         |  .settings(
//         |    name := "foo",
//         |    scalaVersion := "${version.minor}",
//         |    libraryDependencies ++= Seq(
//         |      "$GROUP_ID" % $CARET
//         |    )
//         |  )
//         |""".stripMargin,
//    resultText =
//      s"""
//         |lazy val foo = project.in(file("foo"))
//         |  .settings(
//         |    name := "foo",
//         |    scalaVersion := "${version.minor}",
//         |    libraryDependencies ++= Seq(
//         |      $RESULT_DEPENDENCY
//         |    )
//         |  )
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testInProjectSettings_SeqMultilineFirst_CompleteArtifact_InsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |lazy val foo = project.in(file("foo"))
//         |  .settings(
//         |    name := "foo",
//         |    scalaVersion := "${version.minor}",
//         |    libraryDependencies ++= Seq(
//         |      "$GROUP_ID" % "$CARET"
//         |    )
//         |  )
//         |""".stripMargin,
//    resultText =
//      s"""
//         |lazy val foo = project.in(file("foo"))
//         |  .settings(
//         |    name := "foo",
//         |    scalaVersion := "${version.minor}",
//         |    libraryDependencies ++= Seq(
//         |      $RESULT_DEPENDENCY
//         |    )
//         |  )
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testInProjectSettings_SeqMultilineFirst_CompleteArtifactWithDefinedVersion_OutsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |lazy val foo = project.in(file("foo"))
//         |  .settings(
//         |    name := "foo",
//         |    scalaVersion := "${version.minor}",
//         |    libraryDependencies ++= Seq(
//         |      "$GROUP_ID" % $CARET % "0.0.1"
//         |    )
//         |  )
//         |""".stripMargin,
//    resultText =
//      s"""
//         |lazy val foo = project.in(file("foo"))
//         |  .settings(
//         |    name := "foo",
//         |    scalaVersion := "${version.minor}",
//         |    libraryDependencies ++= Seq(
//         |      "$GROUP_ID" % "$ARTIFACT_ID$CARET" % "0.0.1"
//         |    )
//         |  )
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testInProjectSettings_SeqMultilineFirst_CompleteArtifactWithDefinedVersion_InsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |lazy val foo = project.in(file("foo"))
//         |  .settings(
//         |    name := "foo",
//         |    scalaVersion := "${version.minor}",
//         |    libraryDependencies ++= Seq(
//         |      "$GROUP_ID" % "$CARET" % "0.0.1"
//         |    )
//         |  )
//         |""".stripMargin,
//    resultText =
//      s"""
//         |lazy val foo = project.in(file("foo"))
//         |  .settings(
//         |    name := "foo",
//         |    scalaVersion := "${version.minor}",
//         |    libraryDependencies ++= Seq(
//         |      "$GROUP_ID" % "$ARTIFACT_ID$CARET" % "0.0.1"
//         |    )
//         |  )
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testInProjectSettings_SeqMultilineSecond_OutsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |lazy val foo = project.in(file("foo"))
//         |  .settings(
//         |    name := "foo",
//         |    scalaVersion := "${version.minor}",
//         |    libraryDependencies ++= Seq(
//         |      "foo" % "bar" % "baz",
//         |      $CARET
//         |    )
//         |  )
//         |""".stripMargin,
//    resultText =
//      s"""
//         |lazy val foo = project.in(file("foo"))
//         |  .settings(
//         |    name := "foo",
//         |    scalaVersion := "${version.minor}",
//         |    libraryDependencies ++= Seq(
//         |      "foo" % "bar" % "baz",
//         |      $RESULT_DEPENDENCY
//         |    )
//         |  )
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testInProjectSettings_SeqMultilineSecond_InsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |lazy val foo = project.in(file("foo"))
//         |  .settings(
//         |    name := "foo",
//         |    scalaVersion := "${version.minor}",
//         |    libraryDependencies ++= Seq(
//         |      "foo" % "bar" % "baz",
//         |      "$CARET"
//         |    )
//         |  )
//         |""".stripMargin,
//    resultText =
//      s"""
//         |lazy val foo = project.in(file("foo"))
//         |  .settings(
//         |    name := "foo",
//         |    scalaVersion := "${version.minor}",
//         |    libraryDependencies ++= Seq(
//         |      "foo" % "bar" % "baz",
//         |      $RESULT_DEPENDENCY
//         |    )
//         |  )
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testInProjectSettings_SeqMultilineSecond_CompleteArtifact_OutsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |lazy val foo = project.in(file("foo"))
//         |  .settings(
//         |    name := "foo",
//         |    scalaVersion := "${version.minor}",
//         |    libraryDependencies ++= Seq(
//         |      "foo" % "bar" % "baz",
//         |      "$GROUP_ID" % $CARET
//         |    )
//         |  )
//         |""".stripMargin,
//    resultText =
//      s"""
//         |lazy val foo = project.in(file("foo"))
//         |  .settings(
//         |    name := "foo",
//         |    scalaVersion := "${version.minor}",
//         |    libraryDependencies ++= Seq(
//         |      "foo" % "bar" % "baz",
//         |      $RESULT_DEPENDENCY
//         |    )
//         |  )
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testInProjectSettings_SeqMultilineSecond_CompleteArtifact_InsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |lazy val foo = project.in(file("foo"))
//         |  .settings(
//         |    name := "foo",
//         |    scalaVersion := "${version.minor}",
//         |    libraryDependencies ++= Seq(
//         |      "foo" % "bar" % "baz",
//         |      "$GROUP_ID" % "$CARET"
//         |    )
//         |  )
//         |""".stripMargin,
//    resultText =
//      s"""
//         |lazy val foo = project.in(file("foo"))
//         |  .settings(
//         |    name := "foo",
//         |    scalaVersion := "${version.minor}",
//         |    libraryDependencies ++= Seq(
//         |      "foo" % "bar" % "baz",
//         |      $RESULT_DEPENDENCY
//         |    )
//         |  )
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testInProjectSettings_SeqMultilineSecond_CompleteArtifactWithDefinedVersion_OutsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |lazy val foo = project.in(file("foo"))
//         |  .settings(
//         |    name := "foo",
//         |    scalaVersion := "${version.minor}",
//         |    libraryDependencies ++= Seq(
//         |      "foo" % "bar" % "baz",
//         |      "$GROUP_ID" % $CARET % "0.0.1"
//         |    )
//         |  )
//         |""".stripMargin,
//    resultText =
//      s"""
//         |lazy val foo = project.in(file("foo"))
//         |  .settings(
//         |    name := "foo",
//         |    scalaVersion := "${version.minor}",
//         |    libraryDependencies ++= Seq(
//         |      "foo" % "bar" % "baz",
//         |      "$GROUP_ID" % "$ARTIFACT_ID$CARET" % "0.0.1"
//         |    )
//         |  )
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testInProjectSettings_SeqMultilineSecond_CompleteArtifactWithDefinedVersion_InsideOfStringLiteral(): Unit = doTest(
//    fileText =
//      s"""
//         |lazy val foo = project.in(file("foo"))
//         |  .settings(
//         |    name := "foo",
//         |    scalaVersion := "${version.minor}",
//         |    libraryDependencies ++= Seq(
//         |      "foo" % "bar" % "baz",
//         |      "$GROUP_ID" % "$CARET" % "0.0.1"
//         |    )
//         |  )
//         |""".stripMargin,
//    resultText =
//      s"""
//         |lazy val foo = project.in(file("foo"))
//         |  .settings(
//         |    name := "foo",
//         |    scalaVersion := "${version.minor}",
//         |    libraryDependencies ++= Seq(
//         |      "foo" % "bar" % "baz",
//         |      "$GROUP_ID" % "$ARTIFACT_ID$CARET" % "0.0.1"
//         |    )
//         |  )
//         |""".stripMargin,
//    item = LOOKUP_ITEM
//  )

  //region needs `with MockSbt_1_0`
//  @Test
//  def testTopLevel_VariableWithType_OutsideOfStringLiteral(): Unit = doTest(
//    fileText = s"val dep: ModuleID = $CARET",
//    resultText = s"val dep: ModuleID = $RESULT_DEPENDENCY",
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testTopLevel_VariableWithType_WithPrefix_OutsideOfStringLiteral(): Unit = doTest(
//    fileText = s"val dep: ModuleID = sca$CARET",
//    resultText = s"val dep: ModuleID = $RESULT_DEPENDENCY",
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testTopLevel_SeqWithType_OutsideOfStringLiteral(): Unit = doTest(
//    fileText = s"val deps: Seq[ModuleID] = Seq($CARET)",
//    resultText = s"val deps: Seq[ModuleID] = Seq($RESULT_DEPENDENCY)",
//    item = LOOKUP_ITEM
//  )

//  @Test
//  def testTopLevel_SeqWithType_WithPrefix_OutsideOfStringLiteral(): Unit = doTest(
//    fileText = s"val deps: Seq[ModuleID] = Seq(sca$CARET)",
//    resultText = s"val deps: Seq[ModuleID] = Seq($RESULT_DEPENDENCY)",
//    item = LOOKUP_ITEM
//  )
  //endregion

  //region SCL-22717 examples
//  private def setupCachesSCL22717(groupId: String = "org.scalatest", artifactId: String = "scalatest-"): Unit = {
//    val packages = List("2.13", "3").map { versionSuffix =>
//      apiMavenPackage("org.scalatest", s"scalatest-app_$versionSuffix", versionsContainer("3.2.18"))
//    }.asJava
//    PackageSearchClient.instance().updateByQueryCache(groupId, artifactId, packages)
//  }

  // TODO: version tests
  // TODO: in-between artifactId tests -- works fine
  // TODO: groupId tests -- covers `// 2. ref<caret> %% [...] // org` branch!
  // TODO: incomplete definition tests(???)
//  @Test
//  def testSCL22717_1(): Unit = {
//    setupCachesSCL22717()
//    doTest(
//      fileText =
//        s"""
//           |libraryDependencies ++= Seq(
//           |  "org.scalatest" %% "scalatest-$CARET" % "3.2.18" % Test,
//           |  ("org.scalatest" %% "scalatest-" % "3.2.18") % Test,
//           |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test intransitive(),
//           |  ("org.scalatest" %% "scalatest-" % "3.2.18" % Test) intransitive(),
//           |  ("org.scalatest" %% "scalatest-" % "3.2.18" % Test).intransitive(),
//           |  ((("org.scalatest" %% "scalatest-app" % "3.2.18" % Test))) intransitive(),
//           |)
//           |""".stripMargin,
//      resultText =
//        s"""
//           |libraryDependencies ++= Seq(
//           |  "org.scalatest" %% "scalatest-app$CARET" % "3.2.18" % Test,
//           |  ("org.scalatest" %% "scalatest-" % "3.2.18") % Test,
//           |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test intransitive(),
//           |  ("org.scalatest" %% "scalatest-" % "3.2.18" % Test) intransitive(),
//           |  ("org.scalatest" %% "scalatest-" % "3.2.18" % Test).intransitive(),
//           |  ((("org.scalatest" %% "scalatest-app" % "3.2.18" % Test))) intransitive(),
//           |)
//           |""".stripMargin,
//      item = "org.scalatest::scalatest-app"
//    )
//  }

//  @Test
//  def testSCL22717_2(): Unit = {
//    setupCachesSCL22717()
//    doTest(
//      fileText =
//        s"""
//           |libraryDependencies ++= Seq(
//           |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test,
//           |  ("org.scalatest" %% "scalatest-$CARET" % "3.2.18") % Test,
//           |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test intransitive(),
//           |  ("org.scalatest" %% "scalatest-" % "3.2.18" % Test) intransitive(),
//           |  ("org.scalatest" %% "scalatest-" % "3.2.18" % Test).intransitive(),
//           |  ((("org.scalatest" %% "scalatest-app" % "3.2.18" % Test))) intransitive(),
//           |)
//           |""".stripMargin,
//      resultText =
//        s"""
//           |libraryDependencies ++= Seq(
//           |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test,
//           |  ("org.scalatest" %% "scalatest-app$CARET" % "3.2.18") % Test,
//           |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test intransitive(),
//           |  ("org.scalatest" %% "scalatest-" % "3.2.18" % Test) intransitive(),
//           |  ("org.scalatest" %% "scalatest-" % "3.2.18" % Test).intransitive(),
//           |  ((("org.scalatest" %% "scalatest-app" % "3.2.18" % Test))) intransitive(),
//           |)
//           |""".stripMargin,
//      item = "org.scalatest::scalatest-app"
//    )
//  }

//  @Test
//  def testSCL22717_3(): Unit = {
//    setupCachesSCL22717()
//    doTest(
//      fileText =
//        s"""
//           |libraryDependencies ++= Seq(
//           |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test,
//           |  ("org.scalatest" %% "scalatest-" % "3.2.18") % Test,
//           |  "org.scalatest" %% "scalatest-$CARET" % "3.2.18" % Test intransitive(),
//           |  ("org.scalatest" %% "scalatest-" % "3.2.18" % Test) intransitive(),
//           |  ("org.scalatest" %% "scalatest-" % "3.2.18" % Test).intransitive(),
//           |  ((("org.scalatest" %% "scalatest-app" % "3.2.18" % Test))) intransitive(),
//           |)
//           |""".stripMargin,
//      resultText =
//        s"""
//           |libraryDependencies ++= Seq(
//           |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test,
//           |  ("org.scalatest" %% "scalatest-" % "3.2.18") % Test,
//           |  "org.scalatest" %% "scalatest-app$CARET" % "3.2.18" % Test intransitive(),
//           |  ("org.scalatest" %% "scalatest-" % "3.2.18" % Test) intransitive(),
//           |  ("org.scalatest" %% "scalatest-" % "3.2.18" % Test).intransitive(),
//           |  ((("org.scalatest" %% "scalatest-app" % "3.2.18" % Test))) intransitive(),
//           |)
//           |""".stripMargin,
//      item = "org.scalatest::scalatest-app"
//    )
//  }

//  @Test
//  def testSCL22717_4(): Unit = {
//    setupCachesSCL22717()
//    doTest(
//      fileText =
//        s"""
//           |libraryDependencies ++= Seq(
//           |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test,
//           |  ("org.scalatest" %% "scalatest-" % "3.2.18") % Test,
//           |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test intransitive(),
//           |  ("org.scalatest" %% "scalatest-$CARET" % "3.2.18" % Test) intransitive(),
//           |  ("org.scalatest" %% "scalatest-" % "3.2.18" % Test).intransitive(),
//           |  ((("org.scalatest" %% "scalatest-app" % "3.2.18" % Test))) intransitive(),
//           |)
//           |""".stripMargin,
//      resultText =
//        s"""
//           |libraryDependencies ++= Seq(
//           |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test,
//           |  ("org.scalatest" %% "scalatest-" % "3.2.18") % Test,
//           |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test intransitive(),
//           |  ("org.scalatest" %% "scalatest-app$CARET" % "3.2.18" % Test) intransitive(),
//           |  ("org.scalatest" %% "scalatest-" % "3.2.18" % Test).intransitive(),
//           |  ((("org.scalatest" %% "scalatest-app" % "3.2.18" % Test))) intransitive(),
//           |)
//           |""".stripMargin,
//      item = "org.scalatest::scalatest-app"
//    )
//  }

//  @Test
//  def testSCL22717_5(): Unit = {
//    setupCachesSCL22717()
//    doTest(
//      fileText =
//        s"""
//           |libraryDependencies ++= Seq(
//           |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test,
//           |  ("org.scalatest" %% "scalatest-" % "3.2.18") % Test,
//           |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test intransitive(),
//           |  ("org.scalatest" %% "scalatest-" % "3.2.18" % Test) intransitive(),
//           |  ("org.scalatest" %% "scalatest-$CARET" % "3.2.18" % Test).intransitive(),
//           |  ((("org.scalatest" %% "scalatest-app" % "3.2.18" % Test))) intransitive(),
//           |)
//           |""".stripMargin,
//      resultText =
//        s"""
//           |libraryDependencies ++= Seq(
//           |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test,
//           |  ("org.scalatest" %% "scalatest-" % "3.2.18") % Test,
//           |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test intransitive(),
//           |  ("org.scalatest" %% "scalatest-" % "3.2.18" % Test) intransitive(),
//           |  ("org.scalatest" %% "scalatest-app$CARET" % "3.2.18" % Test).intransitive(),
//           |  ((("org.scalatest" %% "scalatest-app" % "3.2.18" % Test))) intransitive(),
//           |)
//           |""".stripMargin,
//      item = "org.scalatest::scalatest-app"
//    )
//  }

//  @Test
//  def testSCL22717_6(): Unit = {
//    setupCachesSCL22717(artifactId = "scalatest-app")
//    doTest(
//      fileText =
//        s"""
//           |libraryDependencies ++= Seq(
//           |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test,
//           |  ("org.scalatest" %% "scalatest-" % "3.2.18") % Test,
//           |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test intransitive(),
//           |  ("org.scalatest" %% "scalatest-" % "3.2.18" % Test) intransitive(),
//           |  ("org.scalatest" %% "scalatest-" % "3.2.18" % Test).intransitive(),
//           |  ((("org.scalatest" %% "scalatest-app$CARET" % "3.2.18" % Test))) intransitive(),
//           |)
//           |""".stripMargin,
//      resultText =
//        s"""
//           |libraryDependencies ++= Seq(
//           |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test,
//           |  ("org.scalatest" %% "scalatest-" % "3.2.18") % Test,
//           |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test intransitive(),
//           |  ("org.scalatest" %% "scalatest-" % "3.2.18" % Test) intransitive(),
//           |  ("org.scalatest" %% "scalatest-" % "3.2.18" % Test).intransitive(),
//           |  ((("org.scalatest" %% "scalatest-app$CARET" % "3.2.18" % Test))) intransitive(),
//           |)
//           |""".stripMargin,
//      item = "org.scalatest::scalatest-app"
//    )
//  }

  ////////
//  @Test
//  def testSCL22717_1_refOrg(): Unit = {
//    setupCachesSCL22717()
//    doTest(
//      fileText =
//        s"""
//           |val org = "org.scalatest"
//           |
//           |libraryDependencies ++= Seq(
//           |  org %% "scalatest-$CARET" % "3.2.18" % Test,
//           |  (org %% "scalatest-" % "3.2.18") % Test,
//           |  org %% "scalatest-" % "3.2.18" % Test intransitive(),
//           |  (org %% "scalatest-" % "3.2.18" % Test) intransitive(),
//           |  (org %% "scalatest-" % "3.2.18" % Test).intransitive(),
//           |  (((org %% "scalatest-app" % "3.2.18" % Test))) intransitive(),
//           |)
//           |""".stripMargin,
//      resultText =
//        s"""
//           |val org = "org.scalatest"
//           |
//           |libraryDependencies ++= Seq(
//           |  org %% "scalatest-app$CARET" % "3.2.18" % Test,
//           |  (org %% "scalatest-" % "3.2.18") % Test,
//           |  org %% "scalatest-" % "3.2.18" % Test intransitive(),
//           |  (org %% "scalatest-" % "3.2.18" % Test) intransitive(),
//           |  (org %% "scalatest-" % "3.2.18" % Test).intransitive(),
//           |  (((org %% "scalatest-app" % "3.2.18" % Test))) intransitive(),
//           |)
//           |""".stripMargin,
//      item = "org.scalatest::scalatest-app"
//    )
//  }

//  @Test
//  def testSCL22717_2_refOrg(): Unit = {
//    setupCachesSCL22717()
//    doTest(
//      fileText =
//        s"""
//           |val org = "org.scalatest"
//           |
//           |libraryDependencies ++= Seq(
//           |  org %% "scalatest-" % "3.2.18" % Test,
//           |  (org %% "scalatest-$CARET" % "3.2.18") % Test,
//           |  org %% "scalatest-" % "3.2.18" % Test intransitive(),
//           |  (org %% "scalatest-" % "3.2.18" % Test) intransitive(),
//           |  (org %% "scalatest-" % "3.2.18" % Test).intransitive(),
//           |  (((org %% "scalatest-app" % "3.2.18" % Test))) intransitive(),
//           |)
//           |""".stripMargin,
//      resultText =
//        s"""
//           |val org = "org.scalatest"
//           |
//           |libraryDependencies ++= Seq(
//           |  org %% "scalatest-" % "3.2.18" % Test,
//           |  (org %% "scalatest-app$CARET" % "3.2.18") % Test,
//           |  org %% "scalatest-" % "3.2.18" % Test intransitive(),
//           |  (org %% "scalatest-" % "3.2.18" % Test) intransitive(),
//           |  (org %% "scalatest-" % "3.2.18" % Test).intransitive(),
//           |  (((org %% "scalatest-app" % "3.2.18" % Test))) intransitive(),
//           |)
//           |""".stripMargin,
//      item = "org.scalatest::scalatest-app"
//    )
//  }

//  @Test
//  def testSCL22717_3_refOrg(): Unit = {
//    setupCachesSCL22717()
//    doTest(
//      fileText =
//        s"""
//           |val org = "org.scalatest"
//           |
//           |libraryDependencies ++= Seq(
//           |  org %% "scalatest-" % "3.2.18" % Test,
//           |  (org %% "scalatest-" % "3.2.18") % Test,
//           |  org %% "scalatest-$CARET" % "3.2.18" % Test intransitive(),
//           |  (org %% "scalatest-" % "3.2.18" % Test) intransitive(),
//           |  (org %% "scalatest-" % "3.2.18" % Test).intransitive(),
//           |  (((org %% "scalatest-app" % "3.2.18" % Test))) intransitive(),
//           |)
//           |""".stripMargin,
//      resultText =
//        s"""
//           |val org = "org.scalatest"
//           |
//           |libraryDependencies ++= Seq(
//           |  org %% "scalatest-" % "3.2.18" % Test,
//           |  (org %% "scalatest-" % "3.2.18") % Test,
//           |  org %% "scalatest-app$CARET" % "3.2.18" % Test intransitive(),
//           |  (org %% "scalatest-" % "3.2.18" % Test) intransitive(),
//           |  (org %% "scalatest-" % "3.2.18" % Test).intransitive(),
//           |  (((org %% "scalatest-app" % "3.2.18" % Test))) intransitive(),
//           |)
//           |""".stripMargin,
//      item = "org.scalatest::scalatest-app"
//    )
//  }

//  @Test
//  def testSCL22717_4_refOrg(): Unit = {
//    setupCachesSCL22717()
//    doTest(
//      fileText =
//        s"""
//           |val org = "org.scalatest"
//           |
//           |libraryDependencies ++= Seq(
//           |  org %% "scalatest-" % "3.2.18" % Test,
//           |  (org %% "scalatest-" % "3.2.18") % Test,
//           |  org %% "scalatest-" % "3.2.18" % Test intransitive(),
//           |  (org %% "scalatest-$CARET" % "3.2.18" % Test) intransitive(),
//           |  (org %% "scalatest-" % "3.2.18" % Test).intransitive(),
//           |  (((org %% "scalatest-app" % "3.2.18" % Test))) intransitive(),
//           |)
//           |""".stripMargin,
//      resultText =
//        s"""
//           |val org = "org.scalatest"
//           |
//           |libraryDependencies ++= Seq(
//           |  org %% "scalatest-" % "3.2.18" % Test,
//           |  (org %% "scalatest-" % "3.2.18") % Test,
//           |  org %% "scalatest-" % "3.2.18" % Test intransitive(),
//           |  (org %% "scalatest-app$CARET" % "3.2.18" % Test) intransitive(),
//           |  (org %% "scalatest-" % "3.2.18" % Test).intransitive(),
//           |  (((org %% "scalatest-app" % "3.2.18" % Test))) intransitive(),
//           |)
//           |""".stripMargin,
//      item = "org.scalatest::scalatest-app"
//    )
//  }

//  @Test
//  def testSCL22717_5_refOrg(): Unit = {
//    setupCachesSCL22717()
//    doTest(
//      fileText =
//        s"""
//           |val org = "org.scalatest"
//           |
//           |libraryDependencies ++= Seq(
//           |  org %% "scalatest-" % "3.2.18" % Test,
//           |  (org %% "scalatest-" % "3.2.18") % Test,
//           |  org %% "scalatest-" % "3.2.18" % Test intransitive(),
//           |  (org %% "scalatest-" % "3.2.18" % Test) intransitive(),
//           |  (org %% "scalatest-$CARET" % "3.2.18" % Test).intransitive(),
//           |  (((org %% "scalatest-app" % "3.2.18" % Test))) intransitive(),
//           |)
//           |""".stripMargin,
//      resultText =
//        s"""
//           |val org = "org.scalatest"
//           |
//           |libraryDependencies ++= Seq(
//           |  org %% "scalatest-" % "3.2.18" % Test,
//           |  (org %% "scalatest-" % "3.2.18") % Test,
//           |  org %% "scalatest-" % "3.2.18" % Test intransitive(),
//           |  (org %% "scalatest-" % "3.2.18" % Test) intransitive(),
//           |  (org %% "scalatest-app$CARET" % "3.2.18" % Test).intransitive(),
//           |  (((org %% "scalatest-app" % "3.2.18" % Test))) intransitive(),
//           |)
//           |""".stripMargin,
//      item = "org.scalatest::scalatest-app"
//    )
//  }

//  @Test
//  def testSCL22717_6_refOrg(): Unit = {
//    setupCachesSCL22717(artifactId = "scalatest-app")
//    doTest(
//      fileText =
//        s"""
//           |val org = "org.scalatest"
//           |
//           |libraryDependencies ++= Seq(
//           |  org %% "scalatest-" % "3.2.18" % Test,
//           |  (org %% "scalatest-" % "3.2.18") % Test,
//           |  org %% "scalatest-" % "3.2.18" % Test intransitive(),
//           |  (org %% "scalatest-" % "3.2.18" % Test) intransitive(),
//           |  (org %% "scalatest-" % "3.2.18" % Test).intransitive(),
//           |  (((org %% "scalatest-app$CARET" % "3.2.18" % Test))) intransitive(),
//           |)
//           |""".stripMargin,
//      resultText =
//        s"""
//           |val org = "org.scalatest"
//           |
//           |libraryDependencies ++= Seq(
//           |  org %% "scalatest-" % "3.2.18" % Test,
//           |  (org %% "scalatest-" % "3.2.18") % Test,
//           |  org %% "scalatest-" % "3.2.18" % Test intransitive(),
//           |  (org %% "scalatest-" % "3.2.18" % Test) intransitive(),
//           |  (org %% "scalatest-" % "3.2.18" % Test).intransitive(),
//           |  (((org %% "scalatest-app$CARET" % "3.2.18" % Test))) intransitive(),
//           |)
//           |""".stripMargin,
//      item = "org.scalatest::scalatest-app"
//    )
//  }

  ////////

//  @Test
//  def testSCL22717_2_inOrg(): Unit = {
//    setupCachesSCL22717(groupId = "org.scala", artifactId = "")
//    doTest(
//      fileText =
//        s"""
//           |libraryDependencies ++= Seq(
//           |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test,
//           |  ("org.scala${CARET}test" %% "scalatest-" % "3.2.18") % Test,
//           |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test intransitive(),
//           |  ("org.scalatest" %% "scalatest-" % "3.2.18" % Test) intransitive(),
//           |  ("org.scalatest" %% "scalatest-" % "3.2.18" % Test).intransitive(),
//           |  ((("org.scalatest" %% "scalatest-app" % "3.2.18" % Test))) intransitive(),
//           |)
//           |""".stripMargin,
//      resultText =
//        s"""
//           |libraryDependencies ++= Seq(
//           |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test,
//           |  ("org.scalatest" %% "scalatest-app$CARET" % "3.2.18") % Test,
//           |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test intransitive(),
//           |  ("org.scalatest" %% "scalatest-" % "3.2.18" % Test) intransitive(),
//           |  ("org.scalatest" %% "scalatest-" % "3.2.18" % Test).intransitive(),
//           |  ((("org.scalatest" %% "scalatest-app" % "3.2.18" % Test))) intransitive(),
//           |)
//           |""".stripMargin,
//      item = "org.scalatest::scalatest-app"
//    )
//  }

//  @Test
//  def testSCL22717_2_inArtifactRef(): Unit = {
//    setupCachesSCL22717(artifactId = "scala")
//
//    doTest(
//      fileText =
//        s"""
//           |libraryDependencies ++= Seq(
//           |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test,
//           |  ("org.scalatest" %% scala${CARET}test)
//           |)
//           |""".stripMargin,
//      resultText =
//        s"""
//           |libraryDependencies ++= Seq(
//           |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test,
//           |  ("org.scalatest" %% "scalatest-app" % "$CARET")
//           |)
//           |""".stripMargin,
//      item = "org.scalatest::scalatest-app"
//    )
//  }
  //endregion
}
