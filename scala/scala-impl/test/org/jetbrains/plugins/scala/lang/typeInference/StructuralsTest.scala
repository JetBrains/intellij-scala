package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

class StructuralsTest extends TypeInferenceTestBase {

  override def folderPath: Path = super.folderPath / "bugs5"

  def testSCL4724(): Unit = doTest {
    """
      |class SCL4724 {
      |  def foo(x: Set[{ val bar: Int }]) = 1
      |  def foo(s: String) = false
      |
      |  /*start*/foo(Set(new { val bar = 1 }) ++ Set(new { val bar = 2 }))/*end*/
      |}
      |//Int
    """.stripMargin.trim
  }

  def testSCL5423(): Unit = doTest {
    """
      |trait Test {
      |  trait SettValue {
      |    type T
      |    def value: T
      |  }
      |  trait Foo { def foo: Int }
      |  type Sett <: SettValue
      |  type BSetting <: Sett { type T = Foo }
      |  def foo(b: BSetting) = /*start*/b.value.foo/*end*/
      |
      |}
      |
      |//Int
    """.stripMargin
  }

  def testSCL12125(): Unit = doTest {
    """
      |trait A {
      |  type X
      |  def getX: X
      |  def printX(x: X): String
      |}
      |
      |object B {
      |  def printProxy(a: A): String = {
      |    a.printX(/*start*/getProxy(a)/*end*/) // Type mismatch, expected: a.X, actual: A#X
      |  }
      |  def getProxy(a: A): a.X = a.getX
      |}
      |//a.X
    """.stripMargin.trim
  }
}
