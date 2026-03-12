package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.{ScalaVersion, TypecheckerTests}
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class OverloadedMethodWithJavaWildcardsTest extends ScalaLightCodeInsightFixtureTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= ScalaVersion.Latest.Scala_2_13

  override protected def additionalLibraries: Seq[LibraryLoader] =
    Seq(IvyManagedLoader(("com.openai" % "openai-java" % "4.26.0").transitive()))

  //SCL-25157
  def testSCL25157(): Unit = {
    checkTextHasNoErrors(
      """
        |import com.openai.models.responses.ResponseOutputText
        |
        |object TestSCL25157 {
        |  def createResponseOutputText(outputText: String): ResponseOutputText = {
        |    val ret = ResponseOutputText.builder()
        |      .text(outputText)
        |      .annotations(java.util.List.of[ResponseOutputText.Annotation]())
        |      .build()
        |    ret
        |  }
        |}
        |""".stripMargin)
  }
}
