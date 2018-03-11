package org.jetbrains.plugins.scala.debugger.positionManager

import org.jetbrains.plugins.scala.{DebuggerTests, SlowTests}
import org.jetbrains.plugins.scala.debugger._
import org.junit.experimental.categories.Category

/**
 * @author Nikolay.Tropin
 */
@Category(Array(classOf[DebuggerTests]))
class GetAllClassesTest extends GetAllClassesTestBase {
  override implicit val version: ScalaVersion = Scala_2_11
}
@Category(Array(classOf[DebuggerTests]))
class GetAllClassesTest_212 extends GetAllClassesTest_212_Base {
  override implicit val version: ScalaVersion = Scala_2_12
}

abstract class GetAllClassesTest_212_Base extends GetAllClassesTestBase {

  override def testForStmt(): Unit = {
    checkGetAllClasses("ForStmt$")
  }

  override def testByNameArgument(): Unit = {
    checkGetAllClasses("ByNameArgument$")
  }

  override def testFunctionExprs(): Unit = {
    checkGetAllClasses("FunctionExprs$")
  }

  override def testByNameParamInBlock(): Unit = {
    checkGetAllClasses("ByNameParamInBlock$")
  }

  override def testPartialFunctionArg(): Unit = {
    checkGetAllClasses("PartialFunctionArg$", "PartialFunctionArg$", "PartialFunctionArg$")
  }

  override def testSimpleTrait(): Unit = {
    checkGetAllClasses("Test")
  }

  override def testAnonfunsInPackageObject() = {
    checkGetAllClassesInFile("packageObject/package.scala") (
      "packageObject.package$",
      "packageObject.package$",
      "packageObject.package$"
    )
  }
}

abstract class GetAllClassesTestBase extends PositionManagerTestBase {
  
  setupFile("simple.scala",
    s"""
      |object Simple {
      |  def main(args: Array[String]) {
      |    $offsetMarker"" $bp
      |  }
      |}
    """.stripMargin.trim)
  
  def testSimple(): Unit = {
    checkGetAllClasses("Simple$")
  }

  setupFile("SimpleClass.scala",
    s"""
       |object SimpleClass {
       |  def main(args: Array[String]) {
       |    new TestClass().foo()
       |    "" $bp
       |  }
       |}
       |
       |class TestClass {
       |  def foo(): Unit = {
       |    $offsetMarker val i = 0
       |  }
       |}
    """.stripMargin.trim)
  def testSimpleClass(): Unit = {
    checkGetAllClasses("TestClass")
  }

  setupFile("SimpleClassWithComplexName.scala",
    s"""
       |object SimpleClassWithComplexName {
       |  def main(args: Array[String]) {
       |    new `Hi there`().foo()
       |    "" $bp
       |  }
       |}
       |
       |class `Hi there` {
       |  def foo(): Unit = {
       |    $offsetMarker val i = 0
       |  }
       |}
    """.stripMargin.trim)
  def testSimpleClassWithComplexName(): Unit = {
    checkGetAllClasses("Hi$u0020there")
  }

  setupFile("SimpleTrait.scala",
    s"""
       |object SimpleTrait extends Test {
       |  def main(args: Array[String]) {
       |    foo()
       |    "" $bp
       |  }
       |}
       |
       |trait Test {
       |  def foo(): Unit = {
       |    $offsetMarker val i = 0
       |  }
       |}
    """.stripMargin.trim)
  def testSimpleTrait(): Unit = {
    checkGetAllClasses("Test$class")
  }

  setupFile("InnerClassInObject.scala",
    s"""
       |object InnerClassInObject {
       |  def main(args: Array[String]) {
       |    new A
       |    ""$bp
       |  }
       |
       |  class A {
       |    def foo(): Unit = {
       |      $offsetMarker""
       |    }
       |  }
       |}
    """.stripMargin.trim)
  def testInnerClassInObject(): Unit = {
    checkGetAllClasses("InnerClassInObject$A")
  }

  setupFile("LocalClassInAnonClass.scala",
    s"""
       |object LocalClassInAnonClass {
       |  def main(args: Array[String]) {
       |    val r = new Runnable() {
       |      class A {
       |        def foo(): Unit = {
       |          $offsetMarker"abc"
       |        }
       |      }
       |
       |      override def run(): Unit = ${offsetMarker}new A().foo()
       |    }
       |    r.run()
       |    ""$bp
       |  }
       |}
    """.stripMargin.trim)
  def testLocalClassInAnonClass(): Unit = {
    checkGetAllClasses("LocalClassInAnonClass$$anon$1$A", "LocalClassInAnonClass$$anon$1")
  }

  setupFile("LocalObject.scala",
    s"""
       |object LocalObject {
       |  def main(args: Array[String]) {
       |    object A {
       |      def foo(): Unit = {
       |        $offsetMarker""
       |      }
       |    }
       |    A.foo()
       |    "" $bp
       |  }
       |}
    """.stripMargin.trim)
  def testLocalObject(): Unit = {
    checkGetAllClasses("LocalObject$A$2$")
  }

  setupFile("LocalClassSymbolicName.scala",
    s"""
       |object LocalClassSymbolicName {
       |  def main(args: Array[String]) {
       |    class !!! {
       |      def foo(): Unit = {
       |        $offsetMarker""
       |      }
       |    }
       |    new !!!().foo()
       |    "" $bp
       |  }
       |}
    """.stripMargin.trim)
  def testLocalClassSymbolicName(): Unit = {
    checkGetAllClasses("LocalClassSymbolicName$$bang$bang$bang$1")
  }

  setupFile("FunctionExprs.scala",
    s"""
       |object FunctionExprs {
       |  def main(args: Array[String]): Unit = {
       |    val list = List(1, 2, 3)
       |    ${offsetMarker}list.filter(x => ${offsetMarker}x < 10).map(${offsetMarker}_ * 12)
       |      .collect {
       |        ${offsetMarker}case i: Int if i < 20 => "aaa" + i
       |        case i => "aa" + i
       |      }.foreach(${offsetMarker}println)
       |
       |    "" $bp
       |  }
       |}
    """.stripMargin.trim)
  def testFunctionExprs(): Unit = {
    checkGetAllClasses("FunctionExprs$", "FunctionExprs$$anonfun$main$1", "FunctionExprs$$anonfun$main$2", "FunctionExprs$$anonfun$main$3", "FunctionExprs$$anonfun$main$4")
  }

  setupFile("ByNameArgument.scala",
    s"""
       |object ByNameArgument {
       |  def main(args: Array[String]) {
       |    Some(1).orElse(${offsetMarker}None).getOrElse(${offsetMarker}2)
       |    "" $bp
       |  }
       |}
    """.stripMargin.trim)
  def testByNameArgument(): Unit = {
    checkGetAllClasses("ByNameArgument$$anonfun$main$1", "ByNameArgument$$anonfun$main$2")
  }

  setupFile("ForStmt.scala",
    s"""
       |object ForStmt {
       |  def main(args: Array[String]) {
       |    val seq = Seq("a", "b", "c")
       |    for {
       |      ${offsetMarker}s <- seq
       |      ${offsetMarker}t <- seq
       |      ${offsetMarker}if s == t
       |    } {
       |      ${offsetMarker}println(s + t)
       |    }
       |    ""$bp
       |  }
       |}
    """.stripMargin.trim)
  def testForStmt(): Unit = {
    checkGetAllClasses("ForStmt$", "ForStmt$$anonfun$main$1", "ForStmt$$anonfun$main$1$$anonfun$apply$1", "ForStmt$$anonfun$main$1$$anonfun$apply$2")
  }

  setupFile("AnonClass.scala",
    s"""
       |object AnonClass {
       |  def main(args: Array[String]) {
       |    val r = new Runnable {
       |      override def run(): Unit = $offsetMarker()
       |    }
       |    ""$bp
       |  }
       |}
    """.stripMargin.trim)
  def testAnonClass(): Unit = {
    checkGetAllClasses("AnonClass$$anon$1")
  }

  setupFile("ByNameParamInBlock.scala",
    s"""
       |object ByNameParamInBlock {
       |  def main (args: Array[String]){
       |    getOrElse(None) {
       |      ""$offsetMarker
       |    }
       |    ""$bp
       |  }
       |
       |  def getOrElse[T](o: Option[T])(default: => T): Unit = {
       |    o.getOrElse(default)
       |  }
       |}
    """.stripMargin.trim)
  def testByNameParamInBlock(): Unit = {
    checkGetAllClasses("ByNameParamInBlock$$anonfun$main$1")
  }

  setupFile("ClassInBlock.scala",
    s"""
       |object ClassInBlock {
       |  def main(args: Array[String]) {
       |    1 match {
       |      case 1 =>
       |        ${offsetMarker}class A {
       |          def foo = "foo"
       |        }
       |        new A().foo
       |        ""$bp
       |    }
       |  }
       |}
    """.stripMargin.trim)
  def testClassInBlock(): Unit = {
    checkGetAllClasses("ClassInBlock$A$1")
  }

  setupFile("PartialFunctionArg.scala",
    s"""
       |object PartialFunctionArg {
       |  def main(args: Array[String]) {
       |    ${offsetMarker}Seq(Option(1)).exists {
       |      case None =>
       |        ${offsetMarker}true
       |      case Some(i) =>
       |        ${offsetMarker}false$bp
       |    }
       |  }
       |}
    """.stripMargin.trim)
  def testPartialFunctionArg(): Unit = {
    checkGetAllClasses("PartialFunctionArg$", "PartialFunctionArg$$anonfun$main$1", "PartialFunctionArg$$anonfun$main$1")
  }


  setupFile("packageObject/package.scala",
  s"""
    |package object packageObject {
    |
    |  def packageMethod(): Unit = {
    |    for {
    |      ${offsetMarker}i <- 1 to 3
    |      ${offsetMarker}j <- 1 to 3
    |    } {
    |      if (i < j)
    |        ()
    |      else {
    |         ${offsetMarker}println("!")
    |      }
    |    }
    |  }
    |}
  """.stripMargin)
  setupFile("AnonfunsInPackageObject.scala",
    s"""
      |import packageObject._
      |
      |object AnonfunsInPackageObject {
      |  def main(args: Array[String]): Unit = {
      |    packageMethod()
      |    $bp""
      |  }
      |}
    """.stripMargin, hasOffsets = false)
  def testAnonfunsInPackageObject(): Unit = {
    checkGetAllClassesInFile("packageObject/package.scala")(
      "packageObject.package$",
      "packageObject.package$$anonfun$packageMethod$1",
      "packageObject.package$$anonfun$packageMethod$1$$anonfun$apply$mcVI$sp$1"
    )
  }

  setupFile("ValueClass.scala",
    s"""
       |object ValueClass {
       |  def main(args: Array[String]): Unit = {
       |    new Wrapper("").double
       |    ""$bp
       |  }
       |
       |  class Wrapper(val s: String) extends AnyVal {
       |    def double: String = {
       |      ${offsetMarker}s + s
       |    }
       |  }
       |}
    """.stripMargin.trim)
  def testValueClass(): Unit = {
    checkGetAllClasses("ValueClass$Wrapper$")
  }
}