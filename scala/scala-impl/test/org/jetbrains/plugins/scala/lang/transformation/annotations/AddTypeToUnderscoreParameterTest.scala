package org.jetbrains.plugins.scala
package lang
package transformation
package annotations

class AddTypeToUnderscoreParameterTest extends TransformerTest(new AddTypeToUnderscoreParameter()) {

  override protected val header: String =
    """
     object O {
       def apply(v: A => Unit) {}
     }
  """

  def testImplicitType(): Unit = check(
    before = "O(_.a())",
    after = "O((_: A).a())"
  )()

  def testSimpleNameBinding(): Unit = check(
    before = "X(_.toString)",
    after = "X((_: Source).toString)"
  )(header =
    s"""
     ${TransformationTest.ScalaSourceHeader}
     object X { def apply(f: Source => Unit) {} }
    """)

  def testExplicitType(): (String, String) => Unit = check(
    before = "O((_: A).a())",
    after = "O((_: A).a())"
  )
}
