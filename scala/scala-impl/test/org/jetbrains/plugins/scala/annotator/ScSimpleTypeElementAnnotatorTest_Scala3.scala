package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class ScSimpleTypeElementAnnotatorTest_Scala3 extends ScalaLightCodeInsightFixtureTestCase with ScalaHighlightingTestLike {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3

  def testTraitConstructor(): Unit =
    assertNoMessages(
      """
        |object A {
        |
        |  given givenSomething:String = "asd"
        |
        |  trait Example[A]
        |
        |
        |  trait TraitSimple[T[_]]
        |
        |  trait TraitWithArgs[T[_]](using something:String)
        |
        |  abstract class AbstractClassWithArgs[T[_]](using something: String)
        |
        |  // it works if the trait does not have parameters
        |  object instance
        |    extends TraitSimple[Example]
        |
        |  // it does not work when the trait has parameters (even implicit parameters)
        |  object instanceFailing
        |    extends TraitWithArgs[Example]
        |
        |  // it work if it is an abstract class
        |  object instanceWorking
        |    extends AbstractClassWithArgs[Example]
        |}
        |""".stripMargin
    )

  def testReferenceToPolymorphicType_InAliasRhs(): Unit =
    assertNoMessages(
      """type MyAlias1 = Option
        |type MyAlias2 = [X] =>> Either // ~ type MyAlias2 = [X] =>> [L, R] =>> Either[L, R]
        |""".stripMargin
    )

  def testReferenceToPolymorphicType_InTypeBound(): Unit =
    assertNoMessages(
      """type MyAlias[F[_] <: Option] = String
        |trait MyTrait[F[_] <: Option]
        |def myFoo[F[_] <: Option]: String = null
        |""".stripMargin
    )

  def testReferenceToPolymorphicType_InTypeBound_Illegal_NotDirectChild(): Unit =
    assertErrorsText(
      """type MyAlias[F[_] <: Option with Either] = String
        |""".stripMargin,
      """Error(Option,Type Option takes type parameters)
        |Error(Either,Type Either takes type parameters)
        |""".stripMargin
    )
}
