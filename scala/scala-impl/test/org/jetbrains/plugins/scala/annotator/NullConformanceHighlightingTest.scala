package org.jetbrains.plugins.scala.annotator

/**
 * `Null` conformance against abstract types / aliases whose explicit lower bound
 * already admits null (`type Pos >: Null`). The reference-type heuristic
 * (`T <: AnyRef`) in `ScalaConformance.NothingNullVisitor` used to reject these,
 * because such a member's upper bound is only `Any` (so `T </: AnyRef`) even though
 * `Null` is its declared lower bound. SCL-21947.
 *
 * Negatives pin that the fix stays conservative: an abstract type whose lower bound
 * does NOT admit null (`>: Int`, or the default `>: Nothing`) still rejects `null`.
 */
class NullConformanceHighlightingTest extends ScalaHighlightingTestBase {
  import Message._

  // `val x: T = null` — the very case the conformance code comments about.
  def testNullAssignedToAbstractTypeWithNullLowerBound(): Unit = assertNothing(errorsFromScalaCode(
    """
      |trait Attachments {
      |  type Pos >: Null
      |  val p: Pos = null
      |}
      |""".stripMargin
  ))

  // Same, reached through a designator-prefixed projection (`Holder#Pos`) rather than
  // `this.Pos`, exercising the shared `NothingNullVisitor` path from another surface.
  def testNullAssignedToProjectedAbstractTypeWithNullLowerBound(): Unit = assertNothing(errorsFromScalaCode(
    """
      |trait Holder {
      |  type Pos >: Null
      |}
      |object Use {
      |  val p: Holder#Pos = null
      |}
      |""".stripMargin
  ))

  // A lower bound that is itself a reference type (`>: String`) also admits null:
  // `String <: Pos` and `Null <: String`, so `Null <: Pos`.
  def testNullAssignedToAbstractTypeWithRefLowerBound(): Unit = assertNothing(errorsFromScalaCode(
    """
      |trait Attachments {
      |  type Pos >: String
      |  val p: Pos = null
      |}
      |""".stripMargin
  ))

  // Negative: lower bound `Int` does not admit null (`Null </: Int`), and the member's
  // upper bound is `Any` (not `AnyRef`), so `null` must be rejected.
  def testNullRejectedForAbstractTypeWithValLowerBound(): Unit = assertMatches(errorsFromScalaCode(
    """
      |trait Attachments {
      |  type Pos >: Int
      |  val p: Pos = null
      |}
      |""".stripMargin
  )) {
    case Error("null", _) :: Nil =>
  }

  // Negative: an unbounded abstract member (`>: Nothing <: Any`) does not admit null;
  // only `Nothing` would conform to it on the left.
  def testNullRejectedForUnboundedAbstractType(): Unit = assertMatches(errorsFromScalaCode(
    """
      |trait Attachments {
      |  type Pos
      |  val p: Pos = null
      |}
      |""".stripMargin
  )) {
    case Error("null", _) :: Nil =>
  }
}
