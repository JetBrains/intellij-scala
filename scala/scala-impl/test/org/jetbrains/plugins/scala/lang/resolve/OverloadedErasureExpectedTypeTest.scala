package org.jetbrains.plugins.scala.lang.resolve

class OverloadedErasureExpectedTypeTest extends SimpleResolveTestBase {

  import SimpleResolveTestBase._

  def testSCL7222(): Unit ={
    doResolveTest(
      s"""
        |trait Test {
        |  class Foo
        |  class Bar
        |  implicit object I1
        |  implicit object I2
        |  def bar(foo: Foo): Bar
        |  def ${REFTGT}foo(f: Foo => Bar)(implicit i1: I1.type)
        |  def foo(f: Foo => (Foo => Bar))(implicit i2: I2.type)
        |
        |  ${REFSRC}foo(f => bar(f))
        |}
      """.stripMargin)
  }

}
