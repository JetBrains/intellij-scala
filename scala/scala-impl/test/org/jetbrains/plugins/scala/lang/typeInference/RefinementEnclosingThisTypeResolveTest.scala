package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.TypecheckerTests
import org.junit.experimental.categories.Category

/**
 * A structural refinement member whose declared type mentions the enclosing class'
 * `this`-type (`val genBCode: SubComponent { val global: Global.this.type }`).
 *
 * Selecting that member through a stable path (`pre.genBCode.global`) used to drop all
 * members of the result: the projection-substitution folded `Global.this` onto the whole
 * `pre.genBCode.global` projection (a self-referential type), which the resolver's recursion
 * guard then pruned. See
 * [[org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ThisTypeSubstitution]].
 */
@Category(Array(classOf[TypecheckerTests]))
class RefinementEnclosingThisTypeResolveTest extends TypeInferenceTestBase {
  def testResolveThroughRefinementMember(): Unit = checkTextHasNoErrors(
    """
      |class Global {
      |  def foo = 42
      |  val genBCode: SubComponent {
      |    val global: Global.this.type
      |  } = ???
      |}
      |abstract class SubComponent {
      |  val global: Global
      |}
      |abstract class D {
      |  val global: Global
      |  val global1: global.type = global.genBCode.global
      |  global1.foo
      |  global.genBCode.global.foo
      |}
      |""".stripMargin
  )

  def testTypeThroughRefinementMember(): Unit = doTest(
    s"""
       |class Global {
       |  def foo = 42
       |  val genBCode: SubComponent {
       |    val global: Global.this.type
       |  } = ???
       |}
       |abstract class SubComponent {
       |  val global: Global
       |}
       |abstract class D {
       |  val global: Global
       |  $START_MARKER global.genBCode.global $END_MARKER
       |}
       |//D.this.global.type
       |""".stripMargin
  )
}
