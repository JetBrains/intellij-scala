package org.jetbrains.plugins.scala.lang.psi.api.statements

import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

/**
 * Marker interface for definitions which follow by `=>` sign, for example: {{{
 *   class Wrapper {
 *    def this(i: Int) = this()
 *    def foo = 1
 *    var mVar = 2
 *    val mVal = 3
 *    lazy val mLazyVal = 4
 *    type X = 42
 *
 *    // Scala 3
 *    given stringParser: StringParser[String] = baseParser(Success(_))
 *  }
 * }}}
 */
trait ScDefinitionWithAssignment extends ScalaPsiElement {
  // todo add base `assigment` method and share implementation between all inheritors if possible
}