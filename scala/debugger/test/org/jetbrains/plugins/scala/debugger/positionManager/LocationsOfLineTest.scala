package org.jetbrains.plugins.scala
package debugger
package positionManager

import java.nio.file.Path

class LocationOfLineTest_2_11 extends LocationsOfLineTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_11
}

class LocationOfLineTest_2_12 extends LocationsOfLineTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_12

  override def testLambdas(): Unit = {
    checkLocationsOfLine()(
      Set(Loc("Lambdas$", "main", 4), Loc("Lambdas$", "$anonfun$main$1", 4)),
      Set(Loc("Lambdas$", "main", 5), Loc("Lambdas$", "$anonfun$main$2", 5), Loc("Lambdas$", "$anonfun$main$3", 5)),
      Set(Loc("Lambdas$", "$anonfun$main$4", 6))
    )
  }

  override def testMultilevel(): Unit = {
    checkLocationsOfLine("test.MultilevelClasses")(
      Set(Loc("test.MultilevelClasses$This$1", "<init>", 20)), //location for constructor is customized
      Set(Loc("test.MultilevelClasses$This$1", "<init>", 6)),
      Set(Loc("test.MultilevelClasses$This$1", "foo", 8)),
      Set(Loc("test.MultilevelClasses$This$1$$anon$1", "<init>", 8)),
      Set(Loc("test.MultilevelClasses$This$1$$anon$1", "run", 10)),
      Set(Loc("test.MultilevelClasses$This$1$$anon$1", "run", 10)),
      Set(Loc("test.MultilevelClasses$This$1$$anon$1", "$anonfun$run$1", 11)),
      Set(Loc("test.MultilevelClasses$", "main", 20))
    )
  }
}

class LocationOfLineTest_2_13 extends LocationsOfLineTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13

  override def testSimple(): Unit = {
    checkLocationsOfLine()(
      Set(Loc("Simple$", "<clinit>", 2), Loc("Simple$", "z", 2)),
      Set(Loc("Simple$", "main", 4)),
      Set(Loc("Simple$", "main", 5)),
      Set(Loc("Simple$", "main", 6)),
      noLocations,
      Set(Loc("Simple$", "foo", 10)),
      noLocations,
      noLocations
    )
  }

  override def testSimpleClass(): Unit = {
    checkLocationsOfLine()(
      Set(Loc("Bar", "<init>", 8)), //location for constructor is customized
      Set(Loc("Bar", "<init>", 9), Loc("Bar", "s", 9)),
      Set(Loc("Bar", "foo", 12))
    )
  }

  override def testMultilevel(): Unit = {
    checkLocationsOfLine("test.MultilevelClasses")(
      Set(Loc("test.MultilevelClasses$This$1", "<init>", 5)), //location for constructor is customized
      Set(Loc("test.MultilevelClasses$This$1", "<init>", 6)),
      Set(Loc("test.MultilevelClasses$This$1", "foo", 8)),
      Set(Loc("test.MultilevelClasses$This$1$$anon$1", "<init>", 8)),
      Set(Loc("test.MultilevelClasses$This$1$$anon$1", "run", 10)),
      Set(Loc("test.MultilevelClasses$This$1$$anon$1", "run", 10)),
      Set(Loc("test.MultilevelClasses$This$1$$anon$1", "$anonfun$run$1", 11)),
      Set(Loc("test.MultilevelClasses$", "main", 20))
    )
  }

  override def testLambdas(): Unit = {
    checkLocationsOfLine()(
      Set(Loc("Lambdas$", "main", 4), Loc("Lambdas$", "$anonfun$main$1", 4)),
      Set(Loc("Lambdas$", "main", 5), Loc("Lambdas$", "$anonfun$main$2", 5), Loc("Lambdas$", "$anonfun$main$3", 5)),
      Set(Loc("Lambdas$", "main", 6), Loc("Lambdas$", "$anonfun$main$4", 6))
    )
  }
}

class LocationOfLineTest_3 extends LocationOfLineTest_2_13 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  override def testLambdas(): Unit = {
    checkLocationsOfLine()(
      Set(Loc("Lambdas$", "main", 4), Loc("Lambdas$", "main$$anonfun$1", 4)),
      Set(Loc("Lambdas$", "main", 5), Loc("Lambdas$", "main$$anonfun$2", 5), Loc("Lambdas$", "main$$anonfun$3", 5)),
      Set(Loc("Lambdas$", "main", 6), Loc("Lambdas$", "main$$anonfun$4", 6))
    )
  }

  override def testLocalFunction(): Unit = {
    checkLocationsOfLine()(
      noLocations,
      Set(Loc("LocalFunction$", "foo$1", 9)),
      Set(Loc("LocalFunction$", "main", 13))
    )
  }

  override def testMultilevel(): Unit = {
    checkLocationsOfLine("test.MultilevelClasses")(
      Set(Loc("test.MultilevelClasses$This$1", "<init>", 5)), //location for constructor is customized
      Set(Loc("test.MultilevelClasses$This$1", "<init>", 6)),
      noLocations,
      noLocations,
      noLocations,
      noLocations,
      noLocations,
      Set(Loc("test.MultilevelClasses$", "main", 20))
    )
  }
}

class LocationsOfLineTest_3_RC extends LocationOfLineTest_3 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_RC
}

abstract class LocationsOfLineTestBase extends PositionManagerTestBase {
  val noLocations = Set.empty[Loc]

  addSourceFile("Simple.scala",
    s"""
       |object Simple {
       |  ${offsetMarker}val z = 1
       |  def main(args: Array[String]): Unit = {
       |    ${offsetMarker}val i = 1
       |    $offsetMarker"asd".substring(i)
       |    ${offsetMarker}foo()
       |  }$offsetMarker
       |
       |  def foo(): Unit = {
       |    $offsetMarker"" $breakpoint
       |  }$offsetMarker
       |}$offsetMarker
       |""".stripMargin.trim)

  def testSimple(): Unit = {
    checkLocationsOfLine()(
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

  addSourceFile("SimpleClass.scala",
    s"""
       |object SimpleClass {
       |  def main(args: Array[String]): Unit = {
       |    val b = new Bar(1)
       |    b.foo() $breakpoint
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
    checkLocationsOfLine()(
      Set(Loc("Bar", "<init>", 14)), //location for constructor is customized
      Set(Loc("Bar", "<init>", 9), Loc("Bar", "s", 9)),
      Set(Loc("Bar", "foo", 12))
    )
  }

  addSourceFile("Lambdas.scala",
    s"""
       |object Lambdas {
       |  def main(args: Array[String]): Unit = {
       |    val list = List(1, 2)
       |    ${offsetMarker}Some(1).getOrElse(2)
       |    ${offsetMarker}list.filter(_ < 10).map(x => "aaa" + x)
       |       .foreach(${offsetMarker}println)
       |    println() $breakpoint
       |  }
       |}
       |""".stripMargin.trim)

  def testLambdas(): Unit = {
    checkLocationsOfLine()(
      Set(Loc("Lambdas$", "main", 4), Loc("Lambdas$$anonfun$main$1", "apply$mcI$sp", 4)),
      Set(Loc("Lambdas$", "main", 5), Loc("Lambdas$$anonfun$main$2", "apply$mcZI$sp", 5), Loc("Lambdas$$anonfun$main$3", "apply", 5)),
      Set(Loc("Lambdas$$anonfun$main$4", "apply", 6))
    )
  }

  addSourceFile("LocalFunction.scala",
    s"""
       |object LocalFunction {
       |
       |  def main(args: Array[String]): Unit = {
       |    def foo(s: String): Unit = {
       |      def bar() = {
       |        $offsetMarker"bar"
       |      }
       |
       |      ${offsetMarker}println(bar())
       |
       |    }
       |
       |    ${offsetMarker}foo("aaa") $breakpoint
       |  }
       |}
       |""".stripMargin.trim)

  def testLocalFunction(): Unit = {
    checkLocationsOfLine()(
      Set(Loc("LocalFunction$", "bar$1", 6)),
      Set(Loc("LocalFunction$", "foo$1", 9)),
      Set(Loc("LocalFunction$", "main", 13))
    )
  }

  addSourceFile(Path.of("test", "MultilevelClasses.scala").toString,
    s"""package test
       |
       |object MultilevelClasses {
       |  def main(args: Array[String]): Unit = {
       |    ${offsetMarker}class This {
       |      ${offsetMarker}val x = 1
       |      def foo(): Unit = {
       |        ${offsetMarker}val runnable = ${offsetMarker}new Runnable {
       |          def run(): Unit = {
       |            ${offsetMarker}val x = $offsetMarker() => {
       |              ${offsetMarker}This.this.x + 1
       |              println() $breakpoint
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
    checkLocationsOfLine("test.MultilevelClasses")(
      Set(Loc("test.MultilevelClasses$This$1", "<init>", 20)), //location for constructor is customized
      Set(Loc("test.MultilevelClasses$This$1", "<init>", 6)),
      Set(Loc("test.MultilevelClasses$This$1", "foo", 8)),
      Set(Loc("test.MultilevelClasses$This$1$$anon$1", "<init>", 8)),
      Set(Loc("test.MultilevelClasses$This$1$$anon$1", "run", 10)),
      Set(Loc("test.MultilevelClasses$This$1$$anon$1", "run", 10)),
      Set(Loc("test.MultilevelClasses$This$1$$anon$1$$anonfun$1", "apply$mcV$sp", 11)),
      Set(Loc("test.MultilevelClasses$", "main", 20))
    )
  }
}
