package org.jetbrains.plugins.scala
package lang.typeInference

import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class ScalaTagsImplicitConversionTest extends ScalaLightCodeInsightFixtureTestCase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_12

  override def additionalLibraries: Seq[LibraryLoader] = Seq(IvyManagedLoader("com.lihaoyi" %% "scalatags" % "0.8.6"))

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
