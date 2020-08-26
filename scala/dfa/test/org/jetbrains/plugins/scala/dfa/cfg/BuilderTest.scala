package org.jetbrains.plugins.scala.dfa
package cfg

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class BuilderTest extends AnyFunSuite with Matchers with BuilderMatchers {
  def newBuilder: Builder[Unit, String, String] = Builder.newBuilder()

  test("single const") {
    val builder = newBuilder
    builder.constant(DfBool.True)

    builder.finish() should disassembleTo(
      """
        |%0 <- DfTrue
        |end
        |""".stripMargin
    )
  }

  test("jump to end") {
    val builder = newBuilder
    val jump = builder.jumpToFuture()
    builder.jumpHere(jump)

    builder.finish() should disassembleTo(
      """
        |jump .L1
        |.L1:
        |end
        |""".stripMargin
    )
  }

  test("jump to end conditionally") {
    val builder = newBuilder
    val cond = builder.constant(DfBool.Top)
    val jump = builder.jumpToFutureIfNot(cond)
    builder.jumpHere(jump)

    builder.finish() should disassembleTo(
      """
        |%0 <- DfBool.Top
        |if not %0 jump .L2
        |.L2:
        |end
        |""".stripMargin
    )
  }
}
