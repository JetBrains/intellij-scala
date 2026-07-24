package org.jetbrains.plugins.scala.annotator.withLibraryDependencies

import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}

class SCL25718HighlightingTest extends ScalaLightCodeInsightFixtureTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_2_13

  override protected def additionalLibraries: Seq[LibraryLoader] =
    Seq(IvyManagedLoader("io.swagger.core.v3" % "swagger-annotations-jakarta" % "2.2.52"))

  override protected def additionalCompilerOptions: Seq[String] =
    Seq("-Xsource:3")

  def testExactReproductionHasNoErrors(): Unit = checkTextHasNoErrors(
    """import io.swagger.v3.oas.annotations.media.Schema
      |import scala.annotation.meta.field
      |
      |case class Foo(
      |  name: Option[String] = None,
      |  @(Schema @field)(`type` = "boolean")
      |  flag: Option[Boolean] = None,
      |  other: Option[String] = None
      |)
      |
      |object Use {
      |  val f = Foo(
      |    name = Some("x"),
      |    flag = Some(true),
      |    other = Some("y")
      |  )
      |}
      |""".stripMargin
  )
}
