package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.ScalaVersion.Scala_2_12
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}

class ScalaTagsImplicitConversionTest extends ScalaLightCodeInsightFixtureTestAdapter {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == Scala_2_12

  override def librariesLoaders: Seq[LibraryLoader] =
    super.librariesLoaders :+ IvyManagedLoader("com.lihaoyi" %% "scalatags" % "0.8.6")

  def testSCL17374(): Unit = checkTextHasNoErrors(
    """
      |import scalatags.Text.all._
      |
      |val test = div(
      |   div(),
      |   Some(div())
      | )
      |""".stripMargin
  )
}
