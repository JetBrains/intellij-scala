package org.jetbrains.plugins.scala.lang.transformation.references

import org.jetbrains.plugins.scala.lang.transformation.TransformerTest

/**
  * @author Pavel Fatin
  */
class PartiallyQualifySimpleReferenceTest extends TransformerTest(PartiallyQualifySimpleReference) {
  def testUnqualified() = check(
    """
     object O {
       def f: A = _
     }
     import O._
    """,
    "f",
    "O.f"
  )

  def testQualified() = check(
    """
     object O {
       def f: A = _
     }
     import O._
    """,
    "O.f",
    "O.f"
  )

  def testIndirect() = check(
    """
     class T {
       def f: A = _
     }
     object O extends T
     import O._
    """,
    "f",
    "O.f"
  )
}
