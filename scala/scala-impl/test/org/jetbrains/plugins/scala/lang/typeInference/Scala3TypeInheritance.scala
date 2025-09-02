package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion, TypecheckerTests}
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class Scala3TypeInheritance extends ScalaLightCodeInsightFixtureTestCase {
  override protected def supportedIn(version: ScalaVersion) = version >= LatestScalaVersions.Scala_3_0

  def testScl24261(): Unit = {
    checkTextHasNoErrors(
      """
        |class Foo {
        |  def method: Foo = ???
        |}
        |
        |class Bar extends Foo {
        |  override def method = ??? : Bar // : Foo in Scala 3
        |
        |  var x = method
        |  x = ??? : Foo // Compiles in Scala 3
        |}
      """.stripMargin
    )
  }
}
