package org.jetbrains.plugins.scala.compiler.zinc

import com.intellij.openapi.module.ModuleManager
import com.intellij.testFramework.CompilerTester
import org.jetbrains.plugins.scala.CompilationTests
import org.jetbrains.plugins.scala.compiler.CompilerMessagesUtil.{assertCompilingScalaSources, assertNoErrorsOrWarnings}
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.junit.Assert.assertNotNull
import org.junit.experimental.categories.Category

import scala.jdk.CollectionConverters._

@Category(Array(classOf[CompilationTests]))
class VeryLongClassNameTest extends ZincTestBase {

  override def setUp(): Unit = {
    super.setUp()

    createProjectSubDirs("project", "src/main/scala")
    createProjectSubFile("project/build.properties", "sbt.version=1.10.0")
    createProjectSubFile("src/main/scala/LongNames.scala",
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
    createProjectConfig(
      """lazy val root = project.in(file("."))
        |  .settings(scalaVersion := "2.13.14")""".stripMargin
    )

    importProject(false)
    ScalaCompilerConfiguration.instanceIn(myProject).incrementalityType = IncrementalityType.SBT

    val modules = ModuleManager.getInstance(myProject).getModules
    rootModule = modules.find(_.getName == "root").orNull
    assertNotNull("Could not find module with name 'root'", rootModule)
    compiler = new CompilerTester(myProject, java.util.Arrays.asList(modules: _*), null, false)
  }

  def testVeryLongClassFileNames(): Unit = {
    val messages = compiler.make().asScala.toSeq
    assertNoErrorsOrWarnings(messages)
    assertCompilingScalaSources(messages, 1)

    findClassFileInRootModule("LongNames$OuterLevelWithVeryVeryVeryLongClassName1$OuterLe$$$$33a930f152b194d33bc475b527dab5d7$$$$yLongClassName19$OuterLevelWithVeryVeryVeryLongClassName20$MalformedNameExample$")
    findClassFileInRootModule("LongNames$OuterLevelWithVeryVeryVeryLongClassName1$OuterLe$$$$33a930f152b194d33bc475b527dab5d7$$$$yLongClassName19$OuterLevelWithVeryVeryVeryLongClassName20$MalformedNameExample")
  }
}
