package org.jetbrains.plugins.scala
package lang
package transformation
package calls

class ExpandUnaryCallTest extends TransformerTest(new ExpandUnaryCall()) {

  override protected val header: String =
    """
     object O {
       def unary_! {}
       def !(p: A) {}
     }
  """

  def testImplicit(): Unit = check(
    before = "!O",
    after = "O.unary_!"
  )()

  def testSynthetic(): Unit = check(
    before = "!true",
    after = "true.unary_!"
  )()

  def testExplicit(): Unit = check(
    before = "O.unary_!",
    after = "O.unary_!"
  )()

  def testOtherPrefix(): Unit = check(
    before = "+O",
    after = "+O"
  )()

  def testOtherMethod(): Unit = check(
    before = "O.!(A)",
    after = "O.!(A)"
  )()

  // TODO test renamed method
}
