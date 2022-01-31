package org.jetbrains.plugins.scala
package debugger
package evaluateExpression

import org.junit.experimental.categories.Category

/**
 * User: Alefas
 * Date: 19.10.11
 */
@Category(Array(classOf[DebuggerTests]))
class ScalaExpressionsEvaluator extends ScalaExpressionsEvaluatorBase {
  override protected def supportedIn(version: ScalaVersion) = version  == LatestScalaVersions.Scala_2_11
}

@Category(Array(classOf[DebuggerTests]))
class ScalaExpressionsEvaluator_212 extends ScalaExpressionsEvaluatorBase {
  override protected def supportedIn(version: ScalaVersion) = version  == LatestScalaVersions.Scala_2_12
}

@Category(Array(classOf[DebuggerTests]))
class ScalaExpressionsEvaluator_213 extends ScalaExpressionsEvaluatorBase {
  override protected def supportedIn(version: ScalaVersion) = version == LatestScalaVersions.Scala_2_13

  override def testSymbolLiteral(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("'aaa", "Symbol(aaa)")
      evalEquals("'aaa.name", "aaa")
    }
  }
}

@Category(Array(classOf[DebuggerTests]))
class ScalaExpressionsEvaluator_3_0 extends ScalaExpressionsEvaluator_213 {
  override protected def supportedIn(version: ScalaVersion) = version >= LatestScalaVersions.Scala_3_0

  override def testPrefixedThis(): Unit = failing(super.testPrefixedThis())

  override def testPostfix(): Unit = failing(super.testPostfix())

  override def testLiteral(): Unit = failing(super.testLiteral())

  override def testArrayCreation(): Unit = failing(super.testArrayCreation())
}


abstract class ScalaExpressionsEvaluatorBase extends ScalaDebuggerTestCase {
  addFileWithBreakpoints("PrefixUnary.scala",
    s"""
      |object PrefixUnary {
      |  class U {
      |    def unary_!(): Boolean = false
      |  }
      |  def main(args: Array[String]): Unit = {
      |    val u = new U
      |    println()$bp
      |  }
      |}
    """.stripMargin.trim()
  )
  def testPrefixUnary(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("!u", "false")
      evalEquals("!true", "false")
    }
  }

  addFileWithBreakpoints("VariousExprs.scala",
    s"""
      |object VariousExprs {
      |  def main(args: Array[String]): Unit = {
      |    println()$bp
      |  }
      |}
    """.stripMargin.trim()
  )
  def testVariousExprs(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("(1, 2, 3)", "(1,2,3)")
      evalEquals("if (true) \"text\"", "undefined")
      evalEquals("if (true) \"text\" else \"next\"", "text")
      evalEquals("if (false) \"text\" else \"next\"", "next")
      evalEquals("\"text\" != null", "true")
    }
  }

  addFileWithBreakpoints("SmartBoxing.scala",
    s"""
      |object SmartBoxing {
      |  def foo[T](x: T)(y: T) = x
      |  def main(args: Array[String]): Unit = {
      |    val tup = (1, 2)
      |    println()$bp
      |  }
      |  def test(tup: (Int,  Int)) = tup._1
      |  def test2(tup: Tuple2[Int,  Int]) = tup._2
      |}
    """.stripMargin.trim()
  )
  def testSmartBoxing(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("test(tup)", "1")
      evalEquals("test((1, 2))", "1")
      evalEquals("test(Tuple2(1, 2))", "1")
      evalEquals("test2(tup)", "2")
      evalEquals("test2((1, 2))", "2")
      evalEquals("test2(Tuple2(1, 2))", "2")
      evalEquals("foo(1)(2)", "1")
      evalEquals("(scala.collection.immutable.HashSet.empty + 1 + 2).size", "2")
    }
  }

  addFileWithBreakpoints("Assignment.scala",
    s"""
      |object Assignment {
      |  var m = 0
      |  def main(args: Array[String]): Unit = {
      |    var z = 1
      |    val y = 0
      |    val x: Array[Array[Int]] = Array(Array(1, 2), Array(2, 3))
      |    println()$bp
      |  }
      |}
    """.stripMargin.trim()
  )
  def testAssignment(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("x(0)(0)", "1")
      evalEquals("x(0)(0) = 2", "2")
      evalEquals("x(0)(0)", "2")
      evalEquals("z", "1")
      evalEquals("z = 2", "2")
      evalEquals("z", "2")
      evalEquals("m", "0")
      evalEquals("m = 2", "undefined")
      evalEquals("m", "2")
      evalEquals("y = 1", "1") //local vals may be reassigned in debugger
      evalEquals("y", "1")
    }
  }

  addFileWithBreakpoints("This.scala",
    s"""
      |object This {
      |  def main(args: Array[String]): Unit = {
      |    class This {
      |      val x = 1
      |      def foo(): Unit = {
      |       println()$bp
      |      }
      |    }
      |    new This().foo()
      |  }
      |}
    """.stripMargin.trim()
  )
  def testThis(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("this.x", "1")
    }
  }

  addFileWithBreakpoints("PrefixedThis.scala",
    s"""
      |object PrefixedThis {
      |  def main(args: Array[String]): Unit = {
      |    class This {
      |      val x = 1
      |      def foo(): Unit = {
      |        val runnable = new Runnable {
      |          def run(): Unit = {
      |            val x = () => {
      |             This.this.x //to have This.this in scope
      |             println()$bp
      |            }
      |            x()
      |          }
      |        }
      |        runnable.run()
      |      }
      |    }
      |    new This().foo()
      |  }
      |}
    """.stripMargin.trim()
  )
  def testPrefixedThis(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("This.this.x", "1")
    }
  }

  addFileWithBreakpoints("Postfix.scala",
    s"""
      |object Postfix {
      |  def main(args: Array[String]): Unit = {
      |    object x {val x = 1}
      |    x
      |    println()$bp
      |  }
      |}
    """.stripMargin.trim()
  )
  def testPostfix(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("x x", "1")
      evalEquals("1 toString ()", "1")
    }
  }

  addFileWithBreakpoints("Backticks.scala",
    s"""
      |object Backticks {
      |  def main(args: Array[String]): Unit = {
      |    val `val` = 100
      |    println()$bp
      |  }
      |}
    """.stripMargin.trim()
  )
  def testBackticks(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("`val`", "100")
    }
  }

  addFileWithBreakpoints("Literal.scala",
    s"""
      |object Literal {
      |  implicit def intToString(x: Int): String = x.toString + x.toString
      |  def main(args: Array[String]): Unit = {
      |    val n = 1
      |    println()$bp
      |  }
      |}
    """.stripMargin.trim()
  )
  def testLiteral(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals(""""x".length""", "1")
      evalEquals("""s"n = $n"""", "n = 1")
      evalEquals("1L", "1")
      evalEquals("'c'", "c")
      evalEquals("true", "true")
      evalEquals("null", "null")
      evalEquals("1", "1")
      evalEquals("1F", "1.0")
      evalEquals("Array(1F, 2.0F)", "[1.0,2.0]")
      evalEquals("123.charAt(3)", "1")
      evalEquals(""""a".concat(123)""", "a123123")
      evalEquals("intToString(123)", "123123")
    }
  }

  addFileWithBreakpoints("SymbolLiteral.scala",
    s"""object SymbolLiteral {
       |  def main(args: Array[String]): Unit =
       |    println()$bp
       |}
       |""".stripMargin.trim()
  )
  def testSymbolLiteral(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("'aaa", "'aaa")
      evalEquals("'aaa.name", "aaa")
    }
  }

  addFileWithBreakpoints("JavaLib.scala",
    s"""
      |object JavaLib {
      |  def main(args: Array[String]): Unit = {
      |    println()$bp
      |  }
      |}
    """.stripMargin.trim()
  )
  def testJavaLib(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("new StringBuilder(\"test\").append(23)", "test23")
      evalEquals("new Array[Int](2)", "[0,0]")
    }
  }

  addFileWithBreakpoints("InnerClass.scala",
    s"""
      |object InnerClass {
      |  class Expr {}
      |  def main(args: Array[String]): Unit = {
      |    println()$bp
      |  }
      |}
    """.stripMargin.trim()
  )
  def testInnerClass(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalStartsWith("new Expr", "InnerClass$Expr")
    }
  }

  addFileWithBreakpoints("OverloadingClass.scala",
    s"""
      |object OverloadingClass {
      |  class Expr(s: String) {
      |    def this(t: Int) = {
      |      this("test")
      |    }
      |  }
      |  def main(args: Array[String]): Unit = {
      |    println()$bp
      |  }
      |}
    """.stripMargin.trim()
  )
  def testOverloadingClass(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalStartsWith("new Expr(\"\")", "OverloadingClass$Expr")
      evalStartsWith("new Expr(2)", "OverloadingClass$Expr")
    }
  }

  addFileWithBreakpoints("IsInstanceOf.scala",
    s"""
       |object IsInstanceOf {
       |  class A
       |  class B
       |  def main(args: Array[String]): Unit = {
       |    val x = new A
       |    val y = new B
       |    println()$bp
       |  }
       |}
      """.stripMargin.trim()
  )
  def testIsInstanceOf(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("x.isInstanceOf[A]", "true")
      evalEquals("x.isInstanceOf[B]", "false")
      evalEquals("y.isInstanceOf[B]", "true")
      evalEquals("y.isInstanceOf[String]", "false")
      evalEquals("\"\".isInstanceOf[String]", "true")
      evalEquals(""""123".isInstanceOf[Int]""", "false")
    }
  }

  addFileWithBreakpoints("ArrayCreation.scala",
    s"""
       |object ArrayCreation {
       |  def main(args: Array[String]): Unit = {
       |    println()$bp
       |  }
       |}
      """.stripMargin
  )
  def testArrayCreation(): Unit =
    runDebugger() {
      waitForBreakpoint()
      evalEquals("""Array("string1", "string2")""", "[string1,string2]")
      evalEquals("""Array("string1", "string2").isInstanceOf[Array[String]]""", "true")
      evalEquals("""Array("string1", "string2").isInstanceOf[Array[AnyRef]]""", "true")
      evalEquals("""new Array[String](5)""", "[null,null,null,null,null]")
      evalEquals("""new Array[String](5).isInstanceOf[Array[String]]""", "true")
      evalEquals("""new Array[String](5).isInstanceOf[Array[AnyRef]]""", "true")
      evalEquals("""Array(1, 2, 3)""", "[1,2,3]")
      evalEquals("""Array(1, 2, 3).isInstanceOf[Array[Int]]""", "true")
      evalEquals("""Array(1, 2, 3).isInstanceOf[Array[java.lang.Integer]]""", "false")
      evalEquals("""Array[java.lang.Integer](1, 2, 3).isInstanceOf[Array[Int]]""", "false")
      evalEquals("""Array[java.lang.Integer](1, 2, 3).isInstanceOf[Array[java.lang.Integer]]""", "true")
      evalEquals("""Array(1.0, 2.0, 3.0)""", "[1.0,2.0,3.0]")
      evalEquals("""Array(1.0, 2.0, 3.0).isInstanceOf[Array[Double]]""", "true")
      evalEquals("""Array(1.0, 2.0, 3.0).isInstanceOf[Array[java.lang.Double]]""", "false")
      evalEquals("""Array[java.lang.Double](1.0, 2.0, 3.0).isInstanceOf[Array[Double]]""", "false")
      evalEquals("""Array[java.lang.Double](1.0, 2.0, 3.0).isInstanceOf[Array[java.lang.Double]]""", "true")
      evalEquals("""Array(true, false)""", "[true,false]")
      evalEquals("""Array(true, false).isInstanceOf[Array[Boolean]]""", "true")
      evalEquals("""Array(true, false).isInstanceOf[Array[java.lang.Boolean]]""", "false")
      evalEquals("""Array[java.lang.Boolean](true, false).isInstanceOf[Array[Boolean]]""", "false")
      evalEquals("""Array[java.lang.Boolean](true, false).isInstanceOf[Array[java.lang.Boolean]]""", "true")
    }

  addFileWithBreakpoints("SyntheticOperators.scala",
    s"""
       |object SyntheticOperators {
       |  def fail: Boolean = throw new Exception("fail!")
       |  def main(args: Array[String]): Unit = {
       |     val tr = true
       |     val fls = false
       |    println()$bp
       |  }
       |}
      """.stripMargin.trim()
  )
  def testSyntheticOperators(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("tr || fail", "true")
      evalEquals("fls && fail", "false")
      evalEquals("fls || tr", "true")
      evalEquals("tr && fls", "false")
      evalEquals("1 < 1", "false")
      evalEquals("1 <= 1", "true")
      evalEquals("1 + 2", "3")
      evalEquals("3 - 1.5.toInt", "2")
      evalEquals("false ^ true", "true")
      evalEquals("!false", "true")
      evalEquals("false | false", "false")
      evalEquals("1 / 2", "0")
      evalEquals("1 / 2.", "0.5")
      evalEquals("5 % 2", "1")
      evalEquals("1 << 2", "4")
      evalEquals("\"1\" + 1", "11")
      evalEquals("\"123\".##", "123".##.toString)
      evalEquals("42.##", 42.##.toString)
      evalEquals("1.0.##", 1.0.##.toString)
    }
  }

  addFileWithBreakpoints("ValueClasses.scala",
    s"""
       |object ValueClasses {
       |  final case class MetricKilograms1(private val value: Double) extends AnyVal
       |  object MetricKilograms1 {
       |    def kgsString(kgs: MetricKilograms1): String =
       |      kgs.value + "kg"
       |  }
       |
       |  class MetricKilograms2(val value: Double) extends AnyVal {
       |    override def toString(): String = "MetricKilograms2(" + value + ")"
       |  }
       |  def kgs2String(kgs: MetricKilograms2): String =
       |    kgs.value + "kg"
       |
       |  implicit class MetricKilograms3(private val value: Double) extends AnyVal {
       |    def kgString: String = value + "kg"
       |  }
       |  def kgs3String(kgs: MetricKilograms3): String = kgs.kgString
       |
       |  // Test boxing
       |  def genericEquals[A, B](a: A, b: B): Boolean =
       |    a == b
       |
       |  def main(args: Array[String]): Unit = {
       |    val metricValue1 = MetricKilograms1(2.2)
       |    val mvOption1 = Some(metricValue1)
       |
       |    val metricValue2 = new MetricKilograms2(2.2)
       |    val mvOption2 = Some(metricValue2)
       |
       |    val metricValue3: MetricKilograms3 = 2.2
       |    val mvOption3 = Some(metricValue3)
       |
       |    println(MetricKilograms1.kgsString(metricValue1)) $bp
       |  }
       |}
      """.stripMargin.trim()
  )
  def testValueClasses(): Unit =
    runDebugger() {
      waitForBreakpoint()
      evalEquals("metricValue1", "MetricKilograms1(2.2)")
      evalEquals("metricValue1.value", "2.2")
      evalEquals("MetricKilograms1.kgsString(metricValue1)", "2.2kg")
      evalEquals("MetricKilograms1.kgsString(metricValue1.value)", "2.2kg") //doesn't compile but may be evaluated
      evalEquals("mvOption1.get.value", "2.2")
      evalEquals("MetricKilograms1.kgsString(mvOption1.get)", "2.2kg")

      evalEquals("metricValue2", "MetricKilograms2(2.2)")
      evalEquals("metricValue2.value", "2.2")
      evalEquals("kgs2String(metricValue1)", "2.2kg")
      evalEquals("kgs2String(metricValue1.value)", "2.2kg") //doesn't compile but may be evaluated
      evalEquals("mvOption2.get.value", "2.2")
      evalEquals("kgs2String(mvOption2.get)", "2.2kg")

      evalEquals("metricValue3.value", "2.2")
      evalEquals("kgs3String(metricValue3)", "2.2kg")
      evalEquals("kgs3String(metricValue3.value)", "2.2kg") //doesn't compile but may be evaluated
      evalEquals("mvOption3.get.value", "2.2")
      evalEquals("kgs3String(mvOption3.get)", "2.2kg")
      evalEquals("2.2.kgString", "2.2kg")
      evalEquals("kgs3String(2.2)", "2.2kg")

      evalEquals("metricValue1 == metricValue1", "true")
      evalEquals("new MetricKilograms1(2.2) == metricValue1", "true")
      evalEquals("new MetricKilograms1(2.2) == 2.2", "false")
      evalEquals("2.2 == new MetricKilograms1(2.2)", "false")
      evalEquals("(new MetricKilograms1(2.2): MetricKilograms1) == (new MetricKilograms1(2.2): MetricKilograms1)", "true")
      evalEquals("new MetricKilograms1(2.2) == new MetricKilograms2(2.2)", "false")
      evalEquals("new MetricKilograms1(2.2) == new MetricKilograms1(2.2)", "true")
      evalEquals("genericEquals(metricValue1, metricValue1)", "true")
      evalEquals("genericEquals(new MetricKilograms1(2.2), 2.2)", "false")
      evalEquals("genericEquals(2.2, new MetricKilograms1(2.2))", "false")
    }

}