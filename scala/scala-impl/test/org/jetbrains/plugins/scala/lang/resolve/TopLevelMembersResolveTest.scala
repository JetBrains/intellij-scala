package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class TopLevelMembersResolveTest extends SimpleResolveTestBase {

  import SimpleResolveTestBase._

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_3_0

  def testTopLevelSamePackage(): Unit = {
    addFileToProject(
      """
        |package foo
        |type B = Int
        |def foo(): Int = 123
        |
        |case class Foo(i: Int, d: Double)
        |implicit class RichString(val s: String) extends AnyVal {
        | def secondChar: Char = s(1)
        |}
        |""".stripMargin,
      "toplevel.scala"
    )

    doResolveTest(
      s"""
         |package foo
         |object A {
         |  val a = f${REFSRC}oo()
         |}
         |""".stripMargin
    )
    doResolveTest(
      s"""
         |package foo
         |object A {
         |  val v: ${REFSRC}B = 1111123
         |}
         |""".stripMargin
    )
    doResolveTest(
      s"""
         |package foo
         |object A {
         |  new Fo${REFSRC}o(123, 2d)
         |}
         |""".stripMargin
    )
    doResolveTest(
      s"""
         |package foo
         |object A {
         |  val s: String = "123"
         |  s.secondCha${REFSRC}r
         |}
         |""".stripMargin
    )
  }

  def testTopLevelImported(): Unit = {
    addFileToProject(
      """
        |package foo
        |type B = Int
        |def foo(): Int = 123
        |
        |case class Foo(i: Int, d: Double)
        |implicit class RichString(val s: String) extends AnyVal {
        | def secondChar: Char = s(1)
        |}
        |""".stripMargin,
      "toplevel.scala"
    )

    doResolveTest(
      s"""
         |package bar
         |import foo._
         |
         |object A {
         |  val a = f${REFSRC}oo()
         |}
         |""".stripMargin
    )
    doResolveTest(
      s"""
         |package bar
         |import foo.B
         |
         |object A {
         |  val v: ${REFSRC}B = 1111123
         |}
         |""".stripMargin
    )
    doResolveTest(
      s"""
         |package bar
         |import foo._
         |object A {
         |  new Fo${REFSRC}o(123, 2d)
         |}
         |""".stripMargin
    )
    doResolveTest(
      s"""
         |package bar
         |import foo.RichString
         |object A {
         |  "123".secondCha${REFSRC}r
         |}
         |""".stripMargin
    )
  }

  def testTopLevelPrivate(): Unit = {
    addFileToProject(
      """
        |package foo
        |
        |private def bar(): Int = 123
        |""".stripMargin,
      "toplevel.scala"
    )

    doResolveTest(
      s"""
         |package foo
         |object A {
         |  val a = b${REFSRC}ar()
         |}
         |""".stripMargin
    )
    testNoResolve(
      s"""
         |package bar
         |object A {
         |  val a = b${REFSRC}ar()
         |}
         |""".stripMargin -> "noresolve.scala"
    )
  }

  def testDefaultPackage(): Unit = {
    addFileToProject(
      """
        |def bar(): Int = 123
        |""".stripMargin,
      "defaultPackage.scala"
    )

    doResolveTest(
      s"""
         |object A {
         |  val a = b${REFSRC}ar()
         |}
         |""".stripMargin
    )

    testNoResolve(
      s"""
         |package foo
         |
         |object B {
         |  val a = b${REFSRC}ar()
         |}
         |""".stripMargin -> "noresolve.scala"
    )
  }

  def testSCL19625(): Unit = {
    addFileToProject(
      s"""
         |package foo
         |def wrapperFunction(): Unit = {
         |  class MyClassInner1()
         |  trait MyTraitInner1()
         |  object MyObjectInner1
         |  def myInnerFoo: String = ???
         |  val myValueInner1: Int = ???
         |  type myTypeInner1 = String
         |}
         |
         |""".stripMargin,
      "toplevel.scala"
    )

    testNoResolve(
      s"""
         |package foo
         |object A {
         |  new MyCla${REFSRC}ssInner1()
         |}
         |""".stripMargin -> "noresolveclass.scala"
    )

    testNoResolve(
      s"""
         |package foo
         |object A {
         |  new MyTra${REFSRC}itInner1 {}
         |}
         |""".stripMargin -> "noresolvetrait.scala"
    )

    testNoResolve(
      s"""
         |package foo
         |object A {
         |  println(MyObjectInn${REFSRC}er1)
         |}
         |""".stripMargin -> "noresolveobject.scala"
    )

    testNoResolve(
      s"""
         |package foo
         |object A {
         |  println(MyObjectInn${REFSRC}er1)
         |}
         |""".stripMargin -> "noresolveobject.scala"
    )
  }

  private def addFileToProject(text: String, name: String): Unit =
    myFixture.addFileToProject(name, text)
}
