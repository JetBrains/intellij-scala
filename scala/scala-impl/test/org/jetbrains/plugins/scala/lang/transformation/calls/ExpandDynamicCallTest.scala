package org.jetbrains.plugins.scala
package lang
package transformation
package calls

class ExpandDynamicCallTest extends TransformerTest(new ExpandDynamicCall()) {

  override protected val header: String =
    """
     object O extends Dynamic {
       def applyDynamic(s: String)(args: Any*) {}
       def applyDynamicNamed(s: String)(args: (String, Any)*) {}
       def selectDynamic(s: String): Any = _
       def updateDynamic(s: String)(arg: Any) {}
       def f() {}
     }
  """

  def testApplyDynamic(): Unit = check(
    before = "O.foo(A)",
    after = "O.applyDynamic(\"foo\")(A)"
  )()

  def testApplyDynamicInfix(): Unit = check(
    before = "O foo A",
    after = "O.applyDynamic(\"foo\")(A)"
  )()

  def testApplyDynamicNamed(): Unit = check(
    before = "O.foo(bar = A)",
    after = "O.applyDynamicNamed(\"foo\")((\"bar\", A))"
  )()

  def testApplyDynamicNamedInfix(): Unit = check(
    before = "O foo (bar = A)",
    after = "O.applyDynamicNamed(\"foo\")((\"bar\", A))"
  )()

  def testSelectDynamic(): Unit = check(
    before = "O.foo",
    after = "O.selectDynamic(\"foo\")"
  )()

  def testSelectDynamicPostfix(): Unit = check(
    before = "O foo",
    after = "O.selectDynamic(\"foo\")"
  )()

  def testUpdateDynamic(): Unit = check(
    before = "O.foo = A",
    after = "O.updateDynamic(\"foo\")(A)"
  )()

  def testUpdateDynamicPostfix(): Unit = check(
    before = "O foo = A",
    after = "O.updateDynamic(\"foo\")(A)"
  )()

  def testExplicitMethod(): Unit = check(
    before =
      """
       O.applyDynamic("foo")(A)
       O.applyDynamicNamed("foo")(("bar", A))
       O.selectDynamic("foo")
       O.updateDynamic("foo")(A)
    """,
    after =
      """
       O.applyDynamic("foo")(A)
       O.applyDynamicNamed("foo")(("bar", A))
       O.selectDynamic("foo")
       O.updateDynamic("foo")(A)
    """
  )()

  def testRealMethod(): Unit = check(
    before = "O.f()",
    after = "O.f()"
  )()
}
