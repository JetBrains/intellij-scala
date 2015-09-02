package org.jetbrains.plugins.scala.debugger.positionManager

import org.jetbrains.plugins.scala.debugger.Loc

/**
 * @author Nikolay.Tropin
 */
class LocationsOfLineTest extends PositionManagerTestBase {
  val noLocations = Set.empty[Loc]

  def testSimple(): Unit = {

    checkLocationsOfLine(
    s"""object Main {
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
      |""",
        Set(Loc("Main$", "<init>", 2), Loc("Main$", "z", 2)),
        Set(Loc("Main$", "main", 4)),
        Set(Loc("Main$", "main", 5)),
        Set(Loc("Main$", "main", 6)),
        noLocations,
        Set(Loc("Main$", "foo", 10)),
        noLocations,
        noLocations
      )
  }

  def testSimpleClass(): Unit = {
    checkLocationsOfLine(
    s"""
      |object Main {
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
    """,
      Set(Loc("Bar", "<init>", 14)), //location for constructor is customized
      Set(Loc("Bar", "<init>", 9), Loc("Bar", "s", 9)),
      Set(Loc("Bar", "foo", 12))
    )
  }

  def testLambdas(): Unit = {
    checkLocationsOfLine(
    s"""object Main {
      |  def main(args: Array[String]): Unit = {
      |    val list = List(1, 2)
      |    ${offsetMarker}Some(1).getOrElse(2)
      |    ${offsetMarker}list.filter(_ < 10).map(x => "aaa" + x)
      |       .foreach(${offsetMarker}println)
      |    ""$bp
      |  }
      |}""",
      Set(Loc("Main$", "main", 4), Loc("Main$$anonfun$main$1", "apply$mcI$sp", 4)),
      Set(Loc("Main$", "main", 5), Loc("Main$$anonfun$main$2", "apply$mcZI$sp", 5), Loc("Main$$anonfun$main$3", "apply", 5)),
      Set(Loc("Main$$anonfun$main$4", "apply", 6))
    )
  }

  def testLocalFunction(): Unit = {
    checkLocationsOfLine(
      s"""object Main {
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
      """,
      Set(Loc("Main$", "bar$1", 6)),
      Set(Loc("Main$", "foo$1", 9)),
      Set(Loc("Main$", "main", 13))
    )
  }

  def testMultilevel(): Unit = {
    checkLocationsOfLine(
      s"""object Main {
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
        |}""",
      Set(Loc("Main$This$1", "<init>", 18)),  //location for constructor is customized
      Set(Loc("Main$This$1", "<init>", 4)),
      Set(Loc("Main$This$1", "foo", 6)),
      Set(Loc("Main$This$1$$anon$1", "<init>", 6)),
      Set(Loc("Main$This$1$$anon$1", "run", 8)),
      Set(Loc("Main$This$1$$anon$1", "run", 8)),
      Set(Loc("Main$This$1$$anon$1$$anonfun$1", "apply", 9)),
      Set(Loc("Main$", "main", 18))
    )
  }

}
