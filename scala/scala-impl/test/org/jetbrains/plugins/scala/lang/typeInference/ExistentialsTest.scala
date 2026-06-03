package org.jetbrains.plugins.scala.lang.typeInference

class ExistentialsTest extends TypeInferenceTestBase {
  def testSCL4943(): Unit = doTest {
    s"""
      |object SCL4943 {
      |  class Bar {
      |    class Baz
      |  }
      |  class Foo {
      |    def foo = {
      |      val bar = new Bar
      |      object Temp {
      |        def foo(x: (b#Baz forSome { type b >: Bar <: Bar })): Int = 1
      |        def foo(s: String): String = s
      |      }
      |      ${START}Temp.foo(new bar.Baz())$END
      |      new bar.Baz()
      |    }
      |  }
      |}
      |//Int
    """.stripMargin.trim
  }

  def testSCL8634(): Unit = doTest(
    s"""
       |trait Iterable[+S]
       |trait Box[U]
       |trait A {
       |  //val e: Iterable[S] forSome { type U; type S <: Box[U]}
       |  val e: Iterable[S] forSome { type S <: Box[U]; type U}
       |  ${START}e$END
       |}
       |
       |//(Iterable[_ <: Box[U]]) forSome {type U}
    """.stripMargin
  )

}
