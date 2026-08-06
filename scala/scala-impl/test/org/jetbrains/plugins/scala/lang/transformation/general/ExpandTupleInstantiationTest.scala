package org.jetbrains.plugins.scala
package lang
package transformation
package general

class ExpandTupleInstantiationTest extends TransformerTest(new ExpandTupleInstantiation()) {

  def testTuple2(): Unit = check(
    before = "(A, B)",
    after = "Tuple2(A, B)"
  )()

  def testTuple3(): Unit = check(
    before = "(A, B, C)",
    after = "Tuple3(A, B, C)"
  )()

  def testExplicit(): Unit = check(
    before = "Tuple2(A, B)",
    after = "Tuple2(A, B)"
  )()
}
