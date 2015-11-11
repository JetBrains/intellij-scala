package org.jetbrains.plugins.scala.debugger.positionManager

import org.jetbrains.plugins.scala.debugger.{Loc, ScalaVersion_2_11, ScalaVersion_2_12}

/**
 * @author Nikolay.Tropin
 */

class LocationOfLineTest extends LocationsOfLineTestBase with ScalaVersion_2_11

class LocationOfLineTest_212 extends LocationsOfLineTestBase with ScalaVersion_2_12 {

  override def testLambdas(): Unit = {
    checkLocationsOfLine(
      Set(Loc("Lambdas$", "main", 4), Loc("Lambdas$", "Lambdas$$$anonfun$1", 4)),
      Set(Loc("Lambdas$", "main", 5), Loc("Lambdas$", "Lambdas$$$anonfun$2", 5), Loc("Lambdas$", "Lambdas$$$anonfun$3", 5)),
      Set(Loc("Lambdas$", "Lambdas$$$anonfun$4", 6))
    )
  }

  override def testMultilevel(): Unit = {
    checkLocationsOfLine(
      Set(Loc("Multilevel$This$1", "<init>", 18)),  //location for constructor is customized
      Set(Loc("Multilevel$This$1", "<init>", 4)),
      Set(Loc("Multilevel$This$1", "foo", 6)),
      Set(Loc("Multilevel$This$1$$anon$1", "<init>", 6)),
      Set(Loc("Multilevel$This$1$$anon$1", "run", 8)),
      Set(Loc("Multilevel$This$1$$anon$1", "run", 8)),
      Set(Loc("Multilevel$This$1$$anon$1", "Multilevel$This$1$$anon$1$$$anonfun$1", 9)),
      Set(Loc("Multilevel$", "main", 18))
    )

  }
}

abstract class LocationsOfLineTestBase extends PositionManagerTestBase {
  val noLocations = Set.empty[Loc]

  setupFile("Simple.scala",
    s"""
        |object Simple {
        |  ${offsetMarker}val z = 1
        |  def main(args: Array[String]) {
        |    ${offsetMarker}val i = 1
        |    $offsetMarker"asd".substring(i)
        |    ${offsetMarker}foo()
        |  }$offsetMarker
        |
        |  def foo(): Unit = {
        |    $offsetMarker$bp""
        |  }$offsetMarker
        |}$offsetMarker
        |""".stripMargin.trim)
  def testSimple(): Unit = {
    checkLocationsOfLine(
        Set(Loc("Simple$", "<init>", 2), Loc("Simple$", "z", 2)),
        Set(Loc("Simple$", "main", 4)),
        Set(Loc("Simple$", "main", 5)),
        Set(Loc("Simple$", "main", 6)),
        noLocations,
        Set(Loc("Simple$", "foo", 10)),
        noLocations,
        noLocations
      )
  }

  setupFile("SimpleClass.scala",
    s"""
       |object SimpleClass {
       |  def main(args: Array[String]) {
       |    val b = new Bar(1)
       |    b.foo()$bp
       |  }
       |}
       |
       |${offsetMarker}class Bar(i: Int) {
       |  ${offsetMarker}val s = ""
       |
       |  def foo(): Unit = {
       |    $offsetMarker""
       |  }
       |}
       |
       |""".stripMargin.trim)
  def testSimpleClass(): Unit = {
    checkLocationsOfLine(
      Set(Loc("Bar", "<init>", 14)), //location for constructor is customized
      Set(Loc("Bar", "<init>", 9), Loc("Bar", "s", 9)),
      Set(Loc("Bar", "foo", 12))
    )
  }

  setupFile("Lambdas.scala",
    s"""
        |object Lambdas {
        |  def main(args: Array[String]): Unit = {
        |    val list = List(1, 2)
        |    ${offsetMarker}Some(1).getOrElse(2)
        |    ${offsetMarker}list.filter(_ < 10).map(x => "aaa" + x)
        |       .foreach(${offsetMarker}println)
        |    ""$bp
        |  }
        |}
        |""".stripMargin.trim)
  def testLambdas(): Unit = {
    checkLocationsOfLine(
      Set(Loc("Lambdas$", "main", 4), Loc("Lambdas$$anonfun$main$1", "apply$mcI$sp", 4)),
      Set(Loc("Lambdas$", "main", 5), Loc("Lambdas$$anonfun$main$2", "apply$mcZI$sp", 5), Loc("Lambdas$$anonfun$main$3", "apply", 5)),
      Set(Loc("Lambdas$$anonfun$main$4", "apply", 6))
    )
  }

  setupFile("LocalFunction.scala",
    s"""
        |object LocalFunction {
        |
        |  def main(args: Array[String]) {
        |    def foo(s: String): Unit = {
        |      def bar() = {
        |        $offsetMarker"bar"
        |      }
        |
        |      ${offsetMarker}println(bar())
        |
        |    }
        |
        |    ${offsetMarker}foo("aaa") $bp
        |  }
        |}
        |""".stripMargin.trim)
  def testLocalFunction(): Unit = {
    checkLocationsOfLine(
      Set(Loc("LocalFunction$", "bar$1", 6)),
      Set(Loc("LocalFunction$", "foo$1", 9)),
      Set(Loc("LocalFunction$", "main", 13))
    )
  }

  setupFile("Multilevel.scala",
    s"""
        |object Multilevel {
        |  def main(args: Array[String]) {
        |    ${offsetMarker}class This {
        |      ${offsetMarker}val x = 1
        |      def foo() {
        |        ${offsetMarker}val runnable = ${offsetMarker}new Runnable {
        |          def run() {
        |            ${offsetMarker}val x = $offsetMarker() => {
        |              ${offsetMarker}This.this.x
        |              "stop here"$bp
        |            }
        |            x()
        |          }
        |        }
        |        runnable.run()
        |      }
        |    }
        |    ${offsetMarker}new This().foo()
        |  }
        |}""".stripMargin.trim)
  def testMultilevel(): Unit = {
    checkLocationsOfLine(
      Set(Loc("Multilevel$This$1", "<init>", 18)),  //location for constructor is customized
      Set(Loc("Multilevel$This$1", "<init>", 4)),
      Set(Loc("Multilevel$This$1", "foo", 6)),
      Set(Loc("Multilevel$This$1$$anon$1", "<init>", 6)),
      Set(Loc("Multilevel$This$1$$anon$1", "run", 8)),
      Set(Loc("Multilevel$This$1$$anon$1", "run", 8)),
      Set(Loc("Multilevel$This$1$$anon$1$$anonfun$1", "apply", 9)),
      Set(Loc("Multilevel$", "main", 18))
    )
  }

}
