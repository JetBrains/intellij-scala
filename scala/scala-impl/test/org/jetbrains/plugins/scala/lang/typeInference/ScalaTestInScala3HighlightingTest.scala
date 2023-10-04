package org.jetbrains.plugins.scala
package lang.typeInference

import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.junit.experimental.categories.Category


@Category(Array(classOf[TypecheckerTests]))
class ScalaTestInScala3HighlightingTest extends ScalaLightCodeInsightFixtureTestCase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == LatestScalaVersions.Scala_3

  override def additionalLibraries: Seq[LibraryLoader] =
    Seq(IvyManagedLoader(("org.scalatest" %% "scalatest" % "3.2.12").transitive()))

  def testSCL20155(): Unit = checkTextHasNoErrors(
    """
      |import org.scalatest.wordspec.AnyWordSpec
      |
      |class WordSpecTest extends AnyWordSpec {
      |  "parent" which {
      |    "child" ignore { () }
      |    "child" in { () }
      |    "child" is { ??? }
      |    "child" that { () }
      |    "child" when { () }
      |    "child" which { () }
      |
      |    "child" can { () }
      |    "child" must { () }
      |    "child" should { () }
      |  }
      |}
      |""".stripMargin
  )

//  def testSCL20155_2(): Unit = checkTextHasNoErrors(
//    """
//      |import org.scalatest.wordspec.*
//      |
//      |class WordSpecViewTest extends AnyWordSpec {
//      |  "parent1" should {
//      |    "pending1" in pending
//      |    "pending2" is pending
//      |    "pending2".is(pending)
//      |  }
//      |}
//      |""".stripMargin
//  )
}
