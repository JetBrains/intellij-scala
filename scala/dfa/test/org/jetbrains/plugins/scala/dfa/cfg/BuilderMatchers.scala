package org.jetbrains.plugins.scala.dfa
package cfg

import org.scalatest.matchers.{MatchResult, Matcher}
import org.scalatest.matchers.dsl.BeWord

trait BuilderMatchers {
  class DissembleMatcher(expectedAsm: String) extends Matcher[Graph[_]] {
    override def apply(left: Graph[_]): MatchResult = {
      val asm = left.asmText()
      MatchResult(
        asm == expectedAsm,
        s"""${asm} did not equal ${expectedAsm}""",
        s"""${asm} equals ${expectedAsm}"""",
      )
    }
  }

  def disassembleTo(expectedAsm: String): Matcher[Graph[_]] = new DissembleMatcher(expectedAsm.trim + "\n")
}

object BuilderMatchers extends BuilderMatchers