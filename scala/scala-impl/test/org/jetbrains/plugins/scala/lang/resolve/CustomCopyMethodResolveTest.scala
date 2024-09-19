package org.jetbrains.plugins.scala.lang.resolve

class CustomCopyMethodResolveTest extends SimpleResolveTestBase {
  import SimpleResolveTestBase._

  def testSCL15809(): Unit = doResolveTest(
    s"""
       |case class Example(foo: Int) {
       |  private def co${REFTGT}py(foo: Int = this.foo): Example = {
       |    Example(foo + 1)
       |  }
       |  def increase(value: Int): Example = {
       |    cop${REFSRC}y(foo = value)
       |  }
       |}
       |""".stripMargin
  )

  // SCL-23020
  def testConflictingNameInCaseClassObject(): Unit = doResolveTest(
    s"""
       |case class Nest() {
       |  def fa${REFTGT}il: Int = ???
       |}
       |
       |object Nest {
       |  trait Nest
       |}
       |
       |object Test {
       |  Nest().fa${REFSRC}il
       |}
       |""".stripMargin
  )
}
