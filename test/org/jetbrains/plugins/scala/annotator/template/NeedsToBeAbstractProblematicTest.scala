package org.jetbrains.plugins.scala
package annotator
package template

import org.jetbrains.plugins.scala.annotator.AnnotatorTestBase


class NeedsToBeAbstractProblematicTest extends AnnotatorTestBase(NeedsToBeAbstract) {

  def testSCL2981A {
    assertMatches(messages("trait A { type T; def t(p: T)}; class B extends A { type T; def t(p: T) = ()}")) {
      case Nil =>
    }
  }

  def testSCL2981B {
    assertMatches(messages("trait A { type T; def t(p: T)}; class B extends A { type T = Int; def t(p: T) = ()}")) {
      case Nil =>
    }
  }
}