package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase

/**
  * @author Alefas
  * @since 23/03/16
  */
class StructuralsTest extends TypeInferenceTestBase {

  override protected def shouldPass: Boolean = false

  override def folderPath: String = super.folderPath + "bugs5/"

  def testSCL8689(): Unit = doTest()
  
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

  def testSCL3938(): Unit = doTest {
    """
      |object SCL3938 {
      |  type Tagged[U] = {type Tag = U}
      |  type @@[T, U] = T with Tagged[U]
      |
      |  def tag[U, T](t: T): T @@ U = t.asInstanceOf[T @@ U]
      |
      |  trait Day
      |  type Daytime = java.lang.Long @@ Day
      |  type DaytimeScalaLong = Long @@ Day
      |
      |  def foo1(d: Daytime): Int = 1
      |  def foo1(s: String): String = "Text"
      |  def foo2(d: Daytime): Int = 2
      |  def foo2(s: String): String = s
      |  def foo3(d: java.lang.Long @@ Day): Int = 1
      |  def foo3(s: String): String = s
      |  def foo4(d: DaytimeScalaLong): Int = 1
      |  def foo4(s: String): String = s
      |
      |  val i: java.lang.Long = 1L
      |  /*start*/(foo1(tag(i)), foo2(i.asInstanceOf[java.lang.Long @@ Nothing]), foo3(i.asInstanceOf[java.lang.Long @@ Nothing]), foo4(tag(1L)))/*end*/
      |
      |  def daytime1(i: java.lang.Long): Daytime = tag(i) //good code red
      |  def daytime2(i: java.lang.Long): java.lang.Long @@ Day = tag(i) //green code green
      |  def daytime3(i: java.lang.Long): Daytime = i.asInstanceOf[java.lang.Long @@ Nothing] //bad code red
      |  def daytime4(i: java.lang.Long): java.lang.Long @@ Day = i.asInstanceOf[java.lang.Long @@ Nothing] //bad code red
      |
      |  //for some weird reason if you replace java.lang.Long with Long
      |  //his stops compiling and highlighting is correct:
      |
      |  def daytime1(i: Long): DaytimeScalaLong = tag(i) //bad code red
      |}
      |//(Int, Int, Int, Int)
    """.stripMargin.trim
  }

  def testSCL8236(): Unit = doTest {
    """
      |import scala.util.{Try, Failure, Success}
      |class Foo(s: String) {
      |  def doFoo(s: String) = {
      |    doBar1(new Exception {
      |      def doSomething = {
      |        if(true) doBar2(/*start*/Success("", this)/*end*/)
      |        else doBar2(Failure(new Exception()))
      |      }
      |    })
      |  }
      |  def doBar1(e: Exception) = { }
      |  def doBar2(e: Try[(String, Exception)]) = { }
      |}
      |//Try[(String, Exeption)]
    """.stripMargin.trim
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
