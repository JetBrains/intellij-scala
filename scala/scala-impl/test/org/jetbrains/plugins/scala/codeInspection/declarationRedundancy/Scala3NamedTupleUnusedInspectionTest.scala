package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class Scala3NamedTupleUnusedInspectionTest extends Scala3UnusedDeclarationInspectionTestBase {

  override def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_3_6

  def test_named_tuple_components(): Unit = checkTextHasNoErrors(
    """
      |@main
      |def test = {
      |  private type Person = (name: String, age: Int)
      |  private val Bob: Person = (name = "Bob", age = 33)
      |
      |  Bob match {
      |    case (name = _, age = _) => ???
      |  }
      |}
      |""".stripMargin
  )
}
