package org.jetbrains.plugins.scala
package debugger
package positionManager

import java.nio.file.Path

class GetAllClassesTest_2_11 extends GetAllClassesTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_11
}

class GetAllClassesTest_2_12 extends GetAllClassesTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_12

  override def testForStmt(): Unit = {
    checkGetAllClasses()("ForStmt$")
  }

  override def testByNameArgument(): Unit = {
    checkGetAllClasses()("ByNameArgument$")
  }

  override def testFunctionExprs(): Unit = {
    checkGetAllClasses()("FunctionExprs$")
  }

  override def testByNameParamInBlock(): Unit = {
    checkGetAllClasses()("ByNameParamInBlock$")
  }

  override def testPartialFunctionArg(): Unit = {
    checkGetAllClasses()("PartialFunctionArg$", "PartialFunctionArg$", "PartialFunctionArg$")
  }

  override def testSimpleTrait(): Unit = {
    checkGetAllClasses()("Test")
  }

  override def testAnonfunsInPackageObject(): Unit = {
    checkGetAllClassesInFile()(Path.of("packageObject", "package.scala").toString)(
      "packageObject.package$",
      "packageObject.package$",
      "packageObject.package$"
    )
  }

  override def testPartialFunctions(): Unit = {
    checkGetAllClasses()("PartialFunctions$$anonfun$foo$2")
  }

  override def testLocalObject(): Unit = {
    checkGetAllClasses()("LocalObject$A$1$")
  }
}

class GetAllClassesTest_2_13 extends GetAllClassesTest_2_12 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13
}

class GetAllClassesTest_3 extends GetAllClassesTest_2_13 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  override def testPartialFunctions(): Unit = {
    checkGetAllClasses()("PartialFunctions$")
  }

  override def testLocalObject(): Unit = {
    checkGetAllClasses()("LocalObject$A$2$")
  }
}

class GetAllClassesTest_3_RC extends GetAllClassesTest_3 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_RC
}

abstract class GetAllClassesTestBase extends PositionManagerTestBase {

  addSourceFile("Simple.scala",
    s"""
       |object Simple {
       |  def main(args: Array[String]): Unit = {
       |    ${offsetMarker}println() $breakpoint
       |  }
       |}
    """.stripMargin.trim)

  def testSimple(): Unit = {
    checkGetAllClasses()("Simple$")
  }

  addSourceFile("SimpleClass.scala",
    s"""
       |object SimpleClass {
       |  def main(args: Array[String]): Unit = {
       |    new TestClass().foo()
       |    println() $breakpoint
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
    checkGetAllClasses()("TestClass")
  }

  addSourceFile("SimpleClassWithComplexName.scala",
    s"""
       |object SimpleClassWithComplexName {
       |  def main(args: Array[String]): Unit = {
       |    new `Hi there`().foo()
       |    println() $breakpoint
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
    checkGetAllClasses()("Hi$u0020there")
  }

  addSourceFile("SimpleTrait.scala",
    s"""
       |object SimpleTrait extends Test {
       |  def main(args: Array[String]): Unit = {
       |    foo()
       |    println() $breakpoint
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
    checkGetAllClasses()("Test$class")
  }

  addSourceFile("InnerClassInObject.scala",
    s"""
       |object InnerClassInObject {
       |  def main(args: Array[String]): Unit = {
       |    new A
       |    "" $breakpoint
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
    checkGetAllClasses()("InnerClassInObject$A")
  }

  addSourceFile(Path.of("test", "LocalClassInAnonymousClass.scala").toString,
    s"""package test
       |
       |object LocalClassInAnonymousClass {
       |  def main(args: Array[String]): Unit = {
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
       |    println() $breakpoint
       |  }
       |}
    """.stripMargin.trim)

  def testLocalClassInAnonymousClass(): Unit = {
    checkGetAllClasses("test.LocalClassInAnonymousClass")(
      "test.LocalClassInAnonymousClass$$anon$1$A", "test.LocalClassInAnonymousClass$$anon$1"
    )
  }

  addSourceFile("LocalObject.scala",
    s"""
       |object LocalObject {
       |  def main(args: Array[String]): Unit = {
       |    object A {
       |      def foo(): Unit = {
       |        $offsetMarker""
       |      }
       |    }
       |    A.foo()
       |    println() $breakpoint
       |  }
       |}
    """.stripMargin.trim)

  def testLocalObject(): Unit = {
    checkGetAllClasses()("LocalObject$A$2$")
  }

  addSourceFile("LocalClassSymbolicName.scala",
    s"""
       |object LocalClassSymbolicName {
       |  def main(args: Array[String]): Unit = {
       |    class !!! {
       |      def foo(): Unit = {
       |        $offsetMarker""
       |      }
       |    }
       |    new !!!().foo()
       |    println() $breakpoint
       |  }
       |}
    """.stripMargin.trim)

  def testLocalClassSymbolicName(): Unit = {
    checkGetAllClasses()("LocalClassSymbolicName$$bang$bang$bang$1")
  }

  addSourceFile("FunctionExprs.scala",
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
       |    println() $breakpoint
       |  }
       |}
    """.stripMargin.trim)

  def testFunctionExprs(): Unit = {
    checkGetAllClasses()("FunctionExprs$", "FunctionExprs$$anonfun$main$1", "FunctionExprs$$anonfun$main$2", "FunctionExprs$$anonfun$main$3", "FunctionExprs$$anonfun$main$4")
  }

  addSourceFile("ByNameArgument.scala",
    s"""
       |object ByNameArgument {
       |  def main(args: Array[String]): Unit = {
       |    Some(1).orElse(${offsetMarker}None).getOrElse(${offsetMarker}2)
       |    println() $breakpoint
       |  }
       |}
    """.stripMargin.trim)

  def testByNameArgument(): Unit = {
    checkGetAllClasses()("ByNameArgument$$anonfun$main$1", "ByNameArgument$$anonfun$main$2")
  }

  addSourceFile("ForStmt.scala",
    s"""
       |object ForStmt {
       |  def main(args: Array[String]): Unit = {
       |    val seq = Seq("a", "b", "c")
       |    for {
       |      ${offsetMarker}s <- seq
       |      ${offsetMarker}t <- seq
       |      ${offsetMarker}if s == t
       |    } {
       |      ${offsetMarker}println(s + t)
       |    }
       |    "" $breakpoint
       |  }
       |}
    """.stripMargin.trim)

  def testForStmt(): Unit = {
    checkGetAllClasses()("ForStmt$", "ForStmt$$anonfun$main$1", "ForStmt$$anonfun$main$1$$anonfun$apply$1", "ForStmt$$anonfun$main$1$$anonfun$apply$2")
  }

  addSourceFile("AnonClass.scala",
    s"""
       |object AnonClass {
       |  def main(args: Array[String]): Unit = {
       |    val r = new Runnable {
       |      override def run(): Unit = {
       |        ${offsetMarker}println()
       |      }
       |    }
       |    "" $breakpoint
       |  }
       |}
    """.stripMargin.trim)

  def testAnonClass(): Unit = {
    checkGetAllClasses()("AnonClass$$anon$1")
  }

  addSourceFile("ByNameParamInBlock.scala",
    s"""
       |object ByNameParamInBlock {
       |  def main (args: Array[String]): Unit = {
       |    getOrElse(None) {
       |      ""$offsetMarker
       |    }
       |    "" $breakpoint
       |  }
       |
       |  def getOrElse[T](o: Option[T])(default: => T): Unit = {
       |    o.getOrElse(default)
       |  }
       |}
    """.stripMargin.trim)

  def testByNameParamInBlock(): Unit = {
    checkGetAllClasses()("ByNameParamInBlock$$anonfun$main$1")
  }

  addSourceFile("ClassInBlock.scala",
    s"""
       |object ClassInBlock {
       |  def main(args: Array[String]): Unit = {
       |    1 match {
       |      case 1 =>
       |        ${offsetMarker}class A {
       |          def foo = "foo"
       |        }
       |        new A().foo
       |        "" $breakpoint
       |    }
       |  }
       |}
    """.stripMargin.trim)

  def testClassInBlock(): Unit = {
    checkGetAllClasses()("ClassInBlock$A$1")
  }

  addSourceFile("PartialFunctionArg.scala",
    s"""
       |object PartialFunctionArg {
       |  def main(args: Array[String]): Unit = {
       |    ${offsetMarker}Seq(Option(1)).foreach {
       |      case None =>
       |        ${offsetMarker}println()
       |      case Some(i) =>
       |        ${offsetMarker}println() $breakpoint
       |    }
       |  }
       |}
    """.stripMargin.trim)

  def testPartialFunctionArg(): Unit = {
    checkGetAllClasses()("PartialFunctionArg$", "PartialFunctionArg$$anonfun$main$1", "PartialFunctionArg$$anonfun$main$1")
  }


  addSourceFile(Path.of("packageObject", "package.scala").toString,
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
  addSourceFile("AnonfunsInPackageObject.scala",
    s"""
       |import packageObject._
       |
       |object AnonfunsInPackageObject {
       |  def main(args: Array[String]): Unit = {
       |    packageMethod()
       |    "" $breakpoint
       |  }
       |}
    """.stripMargin)

  def testAnonfunsInPackageObject(): Unit = {
    checkGetAllClassesInFile()(Path.of("packageObject", "package.scala").toString)(
      "packageObject.package$",
      "packageObject.package$$anonfun$packageMethod$1",
      "packageObject.package$$anonfun$packageMethod$1$$anonfun$apply$mcVI$sp$1"
    )
  }

  addSourceFile("ValueClass.scala",
    s"""
       |object ValueClass {
       |  def main(args: Array[String]): Unit = {
       |    new Wrapper("").double
       |    "" $breakpoint
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
    checkGetAllClasses()("ValueClass$Wrapper$")
  }

  addSourceFile("PartialFunctions.scala",
    s"""
       |object PartialFunctions {
       |  def main(args: Array[String]): Unit = {
       |    foo()
       |    foo()
       |  }
       |  def foo(): Unit = {
       |    println("foo")
       |    Seq(1).map(x => x * 2)       // ANOTHER LAMBDA
       |    Seq(1).collect { case el =>
       |      println(s"collect") $breakpoint
       |      42$offsetMarker
       |    }
       |  }
       |}
       |""".stripMargin)

  def testPartialFunctions(): Unit = {
    checkGetAllClasses()("PartialFunctions$$anonfun$foo$1")
  }
}