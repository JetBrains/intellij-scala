package org.jetbrains.plugins.scala.lang.transformation.annotations

import org.jetbrains.plugins.scala.lang.transformation.TransformerTest

/**
  * @author Pavel Fatin
  */
class AddTypeToUnderscoreParameterTest extends TransformerTest(AddTypeToUnderscoreParameter,
  """
     object O {
       def apply(v: A => Unit) {}
     }
  """) {

  def testImplicitType() = check(
    "O(_.a())",
    "O((_: A).a())"
  )

  def testExplicitType() = check(
    "O((_: A).a())",
    "O((_: A).a())"
  )
}
