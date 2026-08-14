package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.util.GeneratedHighlightingParameterizedTest
import org.jetbrains.plugins.scala.util.GeneratedParameterizedTestFactory.{SimpleTestData, testDataFromVersionTaggedCode}

/**
 * An anonymous class is local to the expression that creates it, so Scala 3 approximates its type by
 * one that doesn't mention the class, which drops every member that doesn't narrow a member of one of
 * the parents. Scala 2 in contrast infers the full refinement.
 *
 * Whether a member that overrides one of the parents with the very same type is dropped isn't
 * observable by conformance, it is covered by
 * [[org.jetbrains.plugins.scala.codeInsight.intention.types.ToggleTypeAnnotationIntentionTestBase]]
 * instead.
 *
 * These tests are checked against the real compilers by
 * [[org.jetbrains.plugins.scala.lang.typeInference.CheckRefinementApproximationTest_Scala2]] and friends.
 *
 * @see `TypeOps.classBound` and `Typer.ensureNoLocalRefs` in the Scala 3 compiler
 * @see [[org.jetbrains.plugins.scala.lang.psi.impl.expr.ScNewTemplateDefinitionImpl.innerType]]
 */
object RefinementApproximationTest {
  private lazy val testData: Seq[String] = Seq(
    """
      |// RefinementWithoutParents
      |// There is no parent that declares `bar`, so Scala 3 approximates the anonymous class by `Object`
      |val x = new { def bar: Int = 1 }
      |val y: Object { def bar: Int } = x // Error in [Scala3]
      |""".stripMargin,
    """
      |// RefinementOfMemberNotDeclaredByParent
      |trait Foo { def foo: Int }
      |
      |val x = new Foo { override def foo: Int = 1; def bar: Int = 2 }
      |val y: Foo { def bar: Int } = x // Error in [Scala3]
      |""".stripMargin,
    """
      |// RefinementOfNarrowedParentMember
      |// `foo` is declared by the parent with a wider type, so both versions keep the narrowed one
      |trait Foo { def foo: Any }
      |
      |val x = new Foo { override def foo: Int = 1 }
      |val y: Foo { def foo: Int } = x
      |val z: Int = x.foo
      |""".stripMargin,
    """
      |// RefinementOfNarrowedJavaParentMember
      |// `toString` is declared by `Object` with a wider type, so both versions keep the narrowed one
      |val x = new Object { override def toString(): "test" = "test" }
      |val y: "test" = x.toString()
      |""".stripMargin,
    """
      |// RefinementOfTypeMemberNotDeclaredByParent
      |trait Foo
      |
      |val x = new Foo { type T = Int }
      |val y: Foo { type T = Int } = x // Error in [Scala3]
      |""".stripMargin,
    """
      |// RefinementOfAbstractParentTypeMember
      |trait Foo { type T }
      |
      |val x = new Foo { type T = Int }
      |val y: Foo { type T = Int } = x
      |val z: Int = (??? : x.T)
      |""".stripMargin,
    """
      |// RefinementOfBoundedParentTypeMember
      |trait Foo { type T <: AnyVal }
      |
      |val x = new Foo { type T = Int }
      |val y: Foo { type T = Int } = x
      |""".stripMargin,
    """
      |// RefinementWithMultipleParents
      |trait Foo
      |trait Bar
      |
      |val x = new Foo with Bar { def baz: Int = 1 }
      |val y: Foo with Bar = x
      |val z: Foo with Bar { def baz: Int } = x // Error in [Scala3]
      |""".stripMargin,
    """
      |// RefinementWithExpectedType
      |// The approximation would not conform to the expected type, so Scala 3 ascribes the expression to it
      |val x: Object { def bar: Int } = new { def bar: Int = 1 }
      |val y: Object { def bar: Int } = x
      |""".stripMargin,
    """
      |// RefinementWithUndeterminedExpectedType
      |// An expected type that is still an undetermined type parameter tells nothing about the
      |// anonymous class, so it doesn't keep the refinement either
      |def test[T](t: T): T = t
      |
      |val x = test(new { def bar: Int = 1 })
      |val y: Object { def bar: Int } = x // Error in [Scala3]
      |""".stripMargin,
    """
      |// RefinementOfInaccessibleMembers
      |// Members that cannot be accessed from the outside are never part of the refinement
      |trait Foo
      |
      |val x = new Foo { private def a: Int = 1; protected def b: Int = 2; private type T = Int }
      |val y: Foo { def a: Int } = x // Error
      |""".stripMargin
  )

  /** Test data that uses syntax which only exists in Scala 3 and is therefore not run in Scala 2. */
  private lazy val scala3OnlyTestData: Seq[String] = Seq(
    """
      |// RefinementWithSelectableParent
      |// Members of a `Selectable` parent are always kept, selecting them is the whole point of it
      |val x = new reflect.Selectable { def bar: Int = 1 }
      |val y: reflect.Selectable { def bar: Int } = x
      |val z: Int = x.bar
      |""".stripMargin,
    """
      |// RefinementWithSelectableParentAndTypeMember
      |trait Rec extends Selectable {
      |  def selectDynamic(name: String): Any = ???
      |}
      |
      |val x = new Rec { type T = Int; def bar: Int = 1 }
      |val y: Rec { type T = Int; def bar: Int } = x
      |""".stripMargin,
    """
      |// RefinementWithSelectableParentAndUndeterminedExpectedType
      |// In contrast to the cases above, a `Selectable` parent keeps its members no matter
      |// what the expected type is
      |def test[T](t: T): T = t
      |
      |val x = test(new reflect.Selectable { def bar: Int = 1 })
      |val y: reflect.Selectable { def bar: Int } = x
      |""".stripMargin
  )

  lazy val testDataInScala2: Seq[SimpleTestData] =
    testData.map(testDataFromVersionTaggedCode("[Scala3]"))
  lazy val testDataInScala3: Seq[SimpleTestData] =
    (testData ++ scala3OnlyTestData).map(testDataFromVersionTaggedCode("[Scala2]"))
}

abstract class RefinementApproximationTestBase(minScalaVersion: ScalaVersion) extends GeneratedHighlightingParameterizedTest(minScalaVersion) {
  override type TD = SimpleTestData

  override def testData: Seq[TD] =
    if (version.isScala2) RefinementApproximationTest.testDataInScala2
    else RefinementApproximationTest.testDataInScala3
}

class RefinementApproximationTest_Scala2 extends RefinementApproximationTestBase(ScalaVersion.Latest.Scala_2_13)
class RefinementApproximationTest_Scala3 extends RefinementApproximationTestBase(ScalaVersion.Latest.Scala_3_LTS)
