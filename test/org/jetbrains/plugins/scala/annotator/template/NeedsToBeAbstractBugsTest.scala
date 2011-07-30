package org.jetbrains.plugins.scala
package annotator
package template

import org.jetbrains.plugins.scala.annotator.AnnotatorTestBase


class NeedsToBeAbstractBugsTest extends AnnotatorTestBase(NeedsToBeAbstract) {
  def testSCL2981() {
    assertMatches(messages("trait A { type T; def t(p: T)}; class B extends A { type T = Int; def t(p: T) = ()}")) {
      case Nil =>
    }
  }

  def testSCL3515() {
    assertMatches(messages("trait A { type T}; class B extends A")) {
      case Nil =>
    }
  }
}