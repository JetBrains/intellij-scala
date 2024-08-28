package org.jetbrains.plugins.scala.lang.exhaustiveness

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.exhaustiveness.Space
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, TypePresentationContext}
import org.jetbrains.plugins.scala.project.ScalaFeatures
import org.jetbrains.plugins.scala.util.assertions.AssertionMatchers.AssertMatchersExt

class SpaceTest extends ScalaLightCodeInsightFixtureTestCase {
  private implicit val emptyTypePresentationContext: TypePresentationContext = TypePresentationContext.emptyContext
  private def typeSpace(s: String): Space =
    Space.from(ScalaPsiElementFactory.createTypeElementFromText(s, ScalaFeatures.default)(getProject).`type`().get)
  private def patSpace(s: String): Space =
    Space.from(ScalaPsiElementFactory.createPatternFromText(s, ScalaFeatures.default)(getProject))

  private implicit class SpaceExt(space: Space) {
    def shouldShow(expected: String): Unit = {
      space.toReadableString shouldBe expected
    }

    def shouldBeSubSpaceOf(rhs: Space): Unit =
      assert(space.isSubSpaceOf(rhs), s"${rhs.toReadableString} should be a sub-space of ${rhs.toReadableString}")

    def shouldNotBeSubSpaceOf(rhs: Space): Unit =
      assert(!space.isSubSpaceOf(rhs), s"${rhs.toReadableString} should not be a sub-space of ${rhs.toReadableString}")
  }

  private implicit class SpaceSeqExt(spaces: Seq[Space]) {
    def shouldShow(expected: String): Unit = {
      spaces.map(_.toReadableString).sorted.mkString(", ") shouldBe expected
    }
  }


  override def setUp(): Unit = {
    super.setUp()

    myFixture.addFileToProject(
      "test.scala",
      """
        |sealed trait A
        |case class B(a: A) extends A
        |case object O extends A
        |
        |
        |case class C(a1: A, a2: A)
        |""".stripMargin
    )
  }

  def testSpaceCreation(): Unit = {
    typeSpace("Int") shouldShow "_: Int"
    typeSpace("List[Int]") shouldShow "_: List[Int]"

    patSpace("x: Int") shouldShow "_: Int"
    patSpace("_") shouldShow "_"
    patSpace("x") shouldShow "_"
    patSpace("x @ _") shouldShow "_"
    patSpace("(x, y)") shouldShow "(_, _)"
    patSpace("x @ (_: Int, _: String)") shouldShow "(_: Int, _: String)"

    patSpace("List(_, _)") shouldShow "List(_, _)"
    patSpace("Seq(_: Int, _)") shouldShow "Seq(_: Int, _)"
    patSpace("Seq(_: Int | _: String)") shouldShow "Seq(_: Int | _: String)"
    patSpace("None") shouldShow "None"
  }

  def testSpaceSubtraction(): Unit = {
    (typeSpace("A") - typeSpace("B")) shouldShow "O"
    (typeSpace("A") - typeSpace("O.type")) shouldShow "_: B"
    (typeSpace("A") - patSpace("O")) shouldShow "_: B"
    (typeSpace("A") - typeSpace("Int")) shouldShow "_: A"

    (typeSpace("A") - patSpace("B(_)")) shouldShow "O"
    (typeSpace("A") - patSpace("B(O)")) shouldShow "B(_: B) | O"
    (typeSpace("A") - patSpace("B(_: B)")) shouldShow "B(O) | O"

    (typeSpace("Boolean") - typeSpace("true")) shouldShow "false"

    (typeSpace("C") - patSpace("C(_, _)")) shouldShow "empty"
    (typeSpace("C") - patSpace("C(O, O)")) shouldShow "C(_: A, _: B) | C(_: B, _: A)"
    (typeSpace("C") - patSpace("C(O, B(_))")) shouldShow "C(_: A, O) | C(_: B, _: A)"
    (typeSpace("C") - patSpace("C(B(O), O)")) shouldShow "C(B(_: B), _: A) | C(O, _: A) | C(_: A, _: B)"
    (typeSpace("C") - patSpace("C(O, _)")) shouldShow "C(_: A, empty) | C(_: B, _: A)"
    (typeSpace("C") - patSpace("C(_, O)")) shouldShow "C(_: A, _: B) | C(empty, _: A)"
  }

  def testSubSpaceRelationShip(): Unit = {
    typeSpace("A") shouldBeSubSpaceOf typeSpace("A")
    typeSpace("B") shouldBeSubSpaceOf typeSpace("A")
    typeSpace("A") shouldNotBeSubSpaceOf typeSpace("B")

    patSpace("B(_)") shouldBeSubSpaceOf typeSpace("A")
    patSpace("B(O)") shouldBeSubSpaceOf typeSpace("A")
    patSpace("B(B(_))") shouldBeSubSpaceOf typeSpace("A")
    patSpace("B(O)") shouldBeSubSpaceOf patSpace("B(_)")
    patSpace("B(B(_))") shouldBeSubSpaceOf patSpace("B(_)")
    patSpace("B(B(_))") shouldNotBeSubSpaceOf patSpace("B(O)")
  }

  def testFlattenSpace(): Unit = {
    typeSpace("A").flatten shouldShow "_: A"
    patSpace("B(_)").flatten shouldShow "B(_)"
    (typeSpace("A") - patSpace("B(O)")).flatten shouldShow "B(_: B), O"
  }
}
