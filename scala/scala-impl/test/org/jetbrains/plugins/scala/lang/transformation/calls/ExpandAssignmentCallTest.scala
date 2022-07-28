package org.jetbrains.plugins.scala
package lang
package transformation
package calls

class ExpandAssignmentCallTest extends TransformerTest(new ExpandAssignmentCall()) {

  override protected val header: String =
    """
     class T {
       def +(p: A) = new T()
       def ::(p: A) = new T()
     }

     var t = new T()
  """

  def testImplicit(): Unit = check(
    before = "t += A",
    after = "t = t + A"
  )()

  def testSynthetic(): Unit = check(
    before = "i += 1",
    after = "i = i + 1"
  )(header = "var i = 0")

  def testRightAssociative(): Unit = check(
    before = "t ::= A",
    after = "t = A :: t"
  )()

  def testExplicit(): Unit = check(
    before = "t = t + A",
    after = "t = t + A"
  )()

  def testWithoutAssignment(): Unit = check(
    before = "t + A",
    after = "t + A"
  )()

  def testRealMethod(): Unit = check(
    before = "r += A",
    after = "r += A"
  )(header =
    """
     class R {
       def +=(p: A) = new R()
     }

     var r = new R()
    """)
}
