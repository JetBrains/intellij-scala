package org.jetbrains.plugins.scala.lang.transformation.calls

import org.jetbrains.plugins.scala.lang.transformation.TransformerTest

/**
  * @author Pavel Fatin
  */
class ExpandDynamicCallTest extends TransformerTest(ExpandDynamicCall,
  """
     object O extends Dynamic {
       def applyDynamic(s: String)(args: Any*) {}
       def applyDynamicNamed(s: String)(args: (String, Any)*) {}
       def selectDynamic(s: String): Any = _
       def updateDynamic(s: String)(arg: Any) {}
       def f() {}
     }
  """) {

  def testApplyDynamic() = check(
    "O.foo(A)",
    "O.applyDynamic(\"foo\")(A)"
  )

  def testApplyDynamicInfix() = check(
    "O foo A",
    "O.applyDynamic(\"foo\")(A)"
  )

  def testApplyDynamicNamed() = check(
    "O.foo(bar = A)",
    "O.applyDynamicNamed(\"foo\")((\"bar\", A))"
  )

  def testApplyDynamicNamedInfix() = check(
    "O foo (bar = A)",
    "O.applyDynamicNamed(\"foo\")((\"bar\", A))"
  )

  def testSelectDynamic() = check(
    "O.foo",
    "O.selectDynamic(\"foo\")"
  )

  def testSelectDynamicPostfix() = check(
    "O foo",
    "O.selectDynamic(\"foo\")"
  )

  def testUpdateDynamic() = check(
    "O.foo = A",
    "O.updateDynamic(\"foo\")(A)"
  )

  def testUpdateDynamicPostfix() = check(
    "O foo = A",
    "O.updateDynamic(\"foo\")(A)"
  )

  def testExplicitMethod() = check(
    """
       O.applyDynamic("foo")(A)
       O.applyDynamicNamed("foo")(("bar", A))
       O.selectDynamic("foo")
       O.updateDynamic("foo")(A)
    """,
    """
       O.applyDynamic("foo")(A)
       O.applyDynamicNamed("foo")(("bar", A))
       O.selectDynamic("foo")
       O.updateDynamic("foo")(A)
    """
  )

  def testRealMethod() = check(
    "O.f()",
    "O.f()"
  )
}
