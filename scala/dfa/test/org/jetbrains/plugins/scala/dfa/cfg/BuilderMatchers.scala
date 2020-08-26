package org.jetbrains.plugins.scala.dfa
package cfg

import org.scalatest.matchers.Matcher
import org.scalatest.matchers.dsl.BeWord

trait BuilderMatchers {
  private val be = new BeWord

  def disassembleTo(expectedGraphText: String): Matcher[Graph[_]] = be(expectedGraphText.trim + "\n").compose(_.asmText())
}

object BuilderMatchers extends BuilderMatchers