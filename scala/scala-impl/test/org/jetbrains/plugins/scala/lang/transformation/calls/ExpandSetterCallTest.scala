package org.jetbrains.plugins.scala
package lang
package transformation
package calls

class ExpandSetterCallTest extends TransformerTest(new ExpandSetterCall()) {

  override protected val header: String =
    """
     object O {
       var v1: A = _
       var v2: A
       private[this] var v3: A = _
     }
  """

  def testPropertyDefinition(): Unit = check(
    before = "O.v1 = A",
    after = "O.v1_=(A)"
  )()

  def testPropertyDeclaration(): Unit = check(
    before = "O.v2 = A",
    after = "O.v2_=(A)"
  )()

  def testPrivateThisVariable(): Unit = check(
    before = "O.v3 = A",
    after = "O.v3 = A"
  )()

  def testIncomplete(): Unit = check(
    before = "O.v1 =",
    after = "O.v1_=()"
  )()

  def testBlockVariable(): Unit = check(
    before = "v = A",
    after = "v = A"
  )(
    header =
      """
     {
       val v: A = _
    """,
    footer = "}"
  )

  def testExplicitCall(): Unit = check(
    before = "O.v_=(A)",
    after = "O.v_=(A)"
  )()

  // TODO variable in compiled code
}