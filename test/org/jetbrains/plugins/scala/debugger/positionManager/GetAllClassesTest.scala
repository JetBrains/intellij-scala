package org.jetbrains.plugins.scala.debugger.positionManager

import org.jetbrains.plugins.scala.debugger.{ScalaVersion_2_11, ScalaVersion_2_12_M2}

/**
 * @author Nikolay.Tropin
 */

class GetAllClassesTest extends GetAllClassesTestBase with ScalaVersion_2_11

class GetAllClassesTest_2_12_M2 extends GetAllClassesTestBase with ScalaVersion_2_12_M2 {

  override def testForStmt(): Unit = {
    checkGetAllClasses(
      s"""
         |object Main {
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
         |
     """, "Main$"
    )
  }

  override def testByNameArgument(): Unit = {
    checkGetAllClasses(
      s"""
         |object Main {
         |  def main(args: Array[String]) {
         |    Some(1).orElse(${offsetMarker}None).getOrElse(${offsetMarker}2)
         |    "" $bp
         |  }
         |}
    """, "Main$")
  }

  override def testFunctionExprs(): Unit = {
    checkGetAllClasses(
      s"""
         |object Main {
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
    """, "Main$"
    )
  }

  override def testByNameParamInBlock(): Unit = {
    checkGetAllClasses(
      s"""object Main {
          |    def main (args: Array[String]){
          |      getOrElse(None) {
          |        ""$offsetMarker
          |      }
          |      ""$bp
          |    }
          |
        |    def getOrElse[T](o: Option[T])(default: => T): Unit = {
          |      o.getOrElse(default)
          |    }
          |  }
          |
     """, "Main$"
    )
  }

}

abstract class GetAllClassesTestBase extends PositionManagerTestBase {
  def testSimple(): Unit = {
    checkGetAllClasses(
    s"""
      |object Main {
      |  def main(args: Array[String]) {
      |    $offsetMarker"" $bp
      |  }
      |}
    """, "Main$")
  }

  def testSimpleClass(): Unit = {
    checkGetAllClasses(
      s"""object Main {
          |  def main(args: Array[String]) {
          |    new Test().foo()
          |    "" $bp
          |  }
          |}
          |
          |class Test {
          |  def foo(): Unit = {
          |    $offsetMarker val i = 0
          |  }
          |}
        """, "Test")
  }

  def testSimpleClassWithComplexName(): Unit = {
    checkGetAllClasses(
      s"""object Main {
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
        """, "Hi$u0020there")
  }

  def testSimpleTrait(): Unit = {
    checkGetAllClasses(
      s"""object Main extends Test {
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
        """, "Test$class")
  }

  def testInnerClassInObject(): Unit = {
    checkGetAllClasses(
    s"""object Main {
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
      |}""", "Main$A")
  }

  def testLocalClassInAnonClass(): Unit = {
    checkGetAllClasses(
    s"""object Main {
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
      |""", "Main$$anon$1$A", "Main$$anon$1"
    )
  }

  def testLocalObject(): Unit = {
    checkGetAllClasses(
    s"""object Main {
      |  def main(args: Array[String]) {
      |    object A {
      |      def foo(): Unit = {
      |        $offsetMarker""
      |      }
      |    }
      |    A.foo()
      |    "" $bp
      |  }
      |}""", "Main$A$2$"
    )
  }

  def testLocalClassSymbolicName(): Unit = {
    checkGetAllClasses(
      s"""object Main {
          |  def main(args: Array[String]) {
          |    class !!! {
          |      def foo(): Unit = {
          |        $offsetMarker""
          |      }
          |    }
          |    new !!!().foo()
          |    "" $bp
          |  }
          |}""", "Main$$bang$bang$bang$1"
    )
  }

  def testFunctionExprs(): Unit = {
    checkGetAllClasses(
    s"""
      |object Main {
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
    """, "Main$", "Main$$anonfun$main$1", "Main$$anonfun$main$2", "Main$$anonfun$main$3", "Main$$anonfun$main$4"
    )
  }

  def testByNameArgument(): Unit = {
    checkGetAllClasses(
      s"""
        |object Main {
        |  def main(args: Array[String]) {
        |    Some(1).orElse(${offsetMarker}None).getOrElse(${offsetMarker}2)
        |    "" $bp
        |  }
        |}
      """, "Main$$anonfun$main$1", "Main$$anonfun$main$2")
  }

  def testForStmt(): Unit = {
    checkGetAllClasses(
    s"""
       |object Main {
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
       |
     """, "Main$", "Main$$anonfun$main$1", "Main$$anonfun$main$1$$anonfun$apply$1", "Main$$anonfun$main$1$$anonfun$apply$2"
    )
  }

  def testAnonClass(): Unit = {
    checkGetAllClasses(
    s"""object Main {
      |  def main(args: Array[String]) {
      |    val r = new Runnable {
      |      override def run(): Unit = $offsetMarker()
      |    }
      |    ""$bp
      |  }
      |}""", "Main$$anon$1"
    )
  }

  def testByNameParamInBlock(): Unit = {
    checkGetAllClasses(
    s"""object Main {
        |    def main (args: Array[String]){
        |      getOrElse(None) {
        |        ""$offsetMarker
        |      }
        |      ""$bp
        |    }
        |
        |    def getOrElse[T](o: Option[T])(default: => T): Unit = {
        |      o.getOrElse(default)
        |    }
        |  }
        |
     """, "Main$$anonfun$main$1"
    )
  }

  def testClassInBlock(): Unit = {
    checkGetAllClasses(
      s"""object Main {
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
     """, "Main$A$1"
    )
  }
}
