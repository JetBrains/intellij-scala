package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.projectRoots.Sdk
import org.jetbrains.plugins.scala.CompilationTests
import org.jetbrains.plugins.scala.compiler.CompilerMessagesUtil.{assertCompilingScalaSources, assertNoErrorsOrWarnings}
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithJdkVersions, RunWithScalaVersions, TestJdkVersion, TestScalaVersion}
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith

import scala.jdk.CollectionConverters._

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(TestScalaVersion.Scala_2_13))
@RunWithJdkVersions(Array(
  TestJdkVersion.JDK_1_8,
  TestJdkVersion.JDK_11,
  TestJdkVersion.JDK_17
))
@Category(Array(classOf[CompilationTests]))
class VeryLongClassNameTest extends ScalaCompilerTestBase {

  override protected def incrementalityType: IncrementalityType = IncrementalityType.SBT

  override protected def buildProcessJdk: Sdk = CompileServerLauncher.defaultSdk(getProject)

  def testVertLongClassFileName(): Unit = {
    addFileToProjectSources("LongNames.scala",
      """object LongNames {
        |  object OuterLevelWithVeryVeryVeryLongClassName1 {
        |    object OuterLevelWithVeryVeryVeryLongClassName2 {
        |      object OuterLevelWithVeryVeryVeryLongClassName3 {
        |        object OuterLevelWithVeryVeryVeryLongClassName4 {
        |          object OuterLevelWithVeryVeryVeryLongClassName5 {
        |            object OuterLevelWithVeryVeryVeryLongClassName6 {
        |              object OuterLevelWithVeryVeryVeryLongClassName7 {
        |                object OuterLevelWithVeryVeryVeryLongClassName8 {
        |                  object OuterLevelWithVeryVeryVeryLongClassName9 {
        |                    object OuterLevelWithVeryVeryVeryLongClassName10 {
        |                      object OuterLevelWithVeryVeryVeryLongClassName11 {
        |                        object OuterLevelWithVeryVeryVeryLongClassName12 {
        |                          object OuterLevelWithVeryVeryVeryLongClassName13 {
        |                            object OuterLevelWithVeryVeryVeryLongClassName14 {
        |                              object OuterLevelWithVeryVeryVeryLongClassName15 {
        |                                object OuterLevelWithVeryVeryVeryLongClassName16 {
        |                                  object OuterLevelWithVeryVeryVeryLongClassName17 {
        |                                    object OuterLevelWithVeryVeryVeryLongClassName18 {
        |                                      object OuterLevelWithVeryVeryVeryLongClassName19 {
        |                                        object OuterLevelWithVeryVeryVeryLongClassName20 {
        |                                          case class MalformedNameExample(x: Int)
        |                                        }}}}}}}}}}}}}}}}}}}}
        |}
        |""".stripMargin)

    val messages = compiler.make().asScala.toSeq
    assertNoErrorsOrWarnings(messages)
    assertCompilingScalaSources(messages, 1)

    findClassFile("LongNames$OuterLevelWithVeryVeryVeryLongClassName1$OuterLe$$$$33a930f152b194d33bc475b527dab5d7$$$$yLongClassName19$OuterLevelWithVeryVeryVeryLongClassName20$MalformedNameExample$")
    findClassFile("LongNames$OuterLevelWithVeryVeryVeryLongClassName1$OuterLe$$$$33a930f152b194d33bc475b527dab5d7$$$$yLongClassName19$OuterLevelWithVeryVeryVeryLongClassName20$MalformedNameExample")
  }
}
