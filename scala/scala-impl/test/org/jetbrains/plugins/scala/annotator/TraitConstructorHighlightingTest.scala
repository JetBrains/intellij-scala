package org.jetbrains.plugins.scala
package annotator

abstract class TraitConstructorHighlightingTestBase extends ScalaHighlightingTestBase

class TraitConstructorHighlightingTest_Scala2 extends TraitConstructorHighlightingTestBase {
  import Message._

  override protected def supportedIn(version: ScalaVersion): Boolean = version < LatestScalaVersions.Scala_3_0

  def test_trait_params(): Unit = {
    val code =
      """
        |trait Trait(int: Int)(blub: Boolean)
      """.stripMargin

    assertMessagesSorted(errorsFromScalaCode(code))(
      Error("(int: Int)(blub: Boolean)", "Trait parameters require Scala 3.0")
    )
  }
}

class TraitConstructorHighlightingTest_Scala3 extends TraitConstructorHighlightingTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_3_0

  def test_trait_params(): Unit = {
    val code =
      """
        |trait Trait(int: Int)(blub: Boolean)
      """.stripMargin

    assertNothing(errorsFromScalaCode(code))
  }
}

