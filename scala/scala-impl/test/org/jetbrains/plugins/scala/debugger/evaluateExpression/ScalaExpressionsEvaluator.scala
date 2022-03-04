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

  addFileWithBreakpoints("IsInstanceOfWithLiteralTypes.scala",
    s"""
       |object IsInstanceOfWithLiteralTypes {
       |  type LiteralValueAlias = 123
       |  type LiteralRefAlias = "123"
       |  type DoubleValueAlias = LiteralValueAlias
       |  type DoubleRefAlias = LiteralRefAlias
       |
       |  def main(args: Array[String]): Unit = {
       |    println()$bp
       |  }
       |}
      """.stripMargin.trim()
  )
  def testIsInstanceOfWithLiteralTypes(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("123.isInstanceOf[123]", "true")
      evalEquals("123.isInstanceOf[234]", "false")
      evalEquals("123.0.isInstanceOf[123.0f]", "false")
      evalEquals("'c'.isInstanceOf['c']", "true")
      evalStartsWith("""123.isInstanceOf["123"]""", """cannot test if value of type Int is a reference of type "123"""")
      evalEquals("123.isInstanceOf[LiteralValueAlias]", "true")
      evalEquals("456.isInstanceOf[LiteralValueAlias]", "false")
      evalEquals("123.isInstanceOf[DoubleValueAlias]", "true")
      evalEquals("456.isInstanceOf[DoubleValueAlias]", "false")
      evalEquals(""""123".isInstanceOf["234"]""", "false")
      evalEquals(""""123".isInstanceOf[123]""", "false")
      evalEquals(""""123".isInstanceOf[LiteralRefAlias]""", "true")
      evalEquals(""""456".isInstanceOf[LiteralRefAlias]""", "false")
      evalEquals(""""123".isInstanceOf[DoubleRefAlias]""", "true")
      evalEquals(""""456".isInstanceOf[DoubleRefAlias]""", "false")
    }
  }

  addFileWithBreakpoints("ClassOfWithLiteralTypes.scala",
    s"""object ClassOfWithLiteralTypes {
       |  type StringLiteralAlias = "abc"
       |  type DoubleStringLiteralAlias = StringLiteralAlias
       |  type PrimitiveLiteralAlias = 123
       |  type DoublePrimitiveLiteralAlias = PrimitiveLiteralAlias
       |
       |  def main(args: Array[String]): Unit = {
       |    println()$bp
       |  }
       |}""".stripMargin)
  def testClassOfWithLiteralTypes(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalStartsWith("classOf[StringLiteralAlias]", """Literal type ("abc" : String) is not a class type""")
      evalStartsWith("classOf[DoubleStringLiteralAlias]", """Literal type ("abc" : String) is not a class type""")
      evalStartsWith("classOf[PrimitiveLiteralAlias]", "Literal type (123 : Int) is not a class type")
      evalStartsWith("classOf[DoublePrimitiveLiteralAlias]", "Literal type (123 : Int) is not a class type")
    }
  }
}

@Category(Array(classOf[DebuggerTests]))
class ScalaExpressionsEvaluator_3_0 extends ScalaExpressionsEvaluator_213 {
  override protected def supportedIn(version: ScalaVersion) = version >= LatestScalaVersions.Scala_3_0

  override def testPrefixedThis(): Unit = failing(super.testPrefixedThis())

  override def testPostfix(): Unit = failing(super.testPostfix())
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
      evalStartsWith("new Object()", "java.lang.Object@")
      evalStartsWith("new AnyRef()", "java.lang.Object@")
      evalStartsWith("new Any()", "class 'Any' is abstract; cannot be instantiated")
      evalStartsWith("new AnyVal()", "class 'AnyVal' is abstract; cannot be instantiated")
      evalStartsWith("new Unit()", "class 'Unit' is abstract; cannot be instantiated")
      evalStartsWith("new Null()", "class 'Null' is abstract; cannot be instantiated")
      evalStartsWith("new Nothing()", "class 'Nothing' is abstract; cannot be instantiated")
      evalStartsWith("new Singleton()", "trait 'Singleton' is abstract; cannot be instantiated")
      evalEquals("""new String("abc")""", "abc")
      evalEquals("new StringBuilder().## * 0", "0")
      evalEquals("()", "undefined")
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
      |
      |  class Value(val n: Int) extends AnyVal
      |
      |  class StringValue(val s: String) extends AnyVal
      |
      |  var m = 0
      |  def main(args: Array[String]): Unit = {
      |    var z = 1
      |    val y = 0
      |    val x: Array[Array[Int]] = Array(Array(1, 2), Array(2, 3))
      |    val ints: Array[Int] = Array(1, 2)
      |
      |    val boxedAny = Array[Any](1, 2)
      |    val boxedInteger = Array[java.lang.Integer](1, 2)
      |
      |    val boxedValues = Array(new Value(1), new Value(2))
      |    val boxedStrings = Array(new StringValue("1"), new StringValue("2"))
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
      evalEquals("ints(0) = 10", "10")
      evalEquals("ints(0)", "10")
      evalEquals("boxedAny(0) = 10", "10")
      evalEquals("boxedAny(0)", "10")
      evalEquals("boxedInteger(0) = 10", "10")
      evalEquals("boxedInteger(0)", "10")
      evalEquals("(boxedValues(0) = new Value(19)) == new Value(19)", "true")
      evalEquals("boxedValues(0) == new Value(19)", "true")
      evalEquals("(boxedValues(0) = (((((((((new Value(20))))))))))) == new Value(20)", "true")
      evalEquals("""(boxedStrings(0) = new StringValue("19")) == new StringValue("19")""", "true")
      evalEquals("""boxedStrings(0) == new StringValue("19")""", "true")
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
       |
       |  class Value(val v: Int) extends AnyVal
       |
       |  type Alias = String
       |
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
      evalEquals("123.isInstanceOf[Int]", "true")
      evalEquals("123.0f.isInstanceOf[Float]", "true")
      evalEquals("123.isInstanceOf[Float]", "false")
      evalEquals("123.0f.isInstanceOf[Double]", "false")
      evalEquals("123.isInstanceOf[Byte]", "false")
      evalEquals("123.0.isInstanceOf[Double]", "true")
      evalEquals("123.0.isInstanceOf[Float]", "false")
      evalEquals("123.isInstanceOf[Short]", "false")
      evalEquals("123.isInstanceOf[Long]", "false")
      evalEquals("123L.isInstanceOf[Long]", "true")
      evalEquals("123L.isInstanceOf[Int]", "false")
      evalStartsWith("123.isInstanceOf[String]", "cannot test if value of type Int is a reference of type String")
      evalEquals(""""123".isInstanceOf[String]""", "true")
      evalEquals(""""123".isInstanceOf[Long]""", "false")
      evalEquals(""""123".isInstanceOf[AnyRef]""", "true")
      evalEquals(""""123".isInstanceOf[Any]""", "true")
      evalStartsWith(""""123".isInstanceOf[AnyVal]""", "class AnyVal cannot be used in runtime type tests")
      evalEquals("null.isInstanceOf[String]", "false")
      evalStartsWith("null.isInstanceOf[Null]", "class Null cannot be used in runtime type tests")
      evalStartsWith("123.isInstanceOf[Value]", "cannot test if value of type Int is a reference of type IsInstanceOf.Value")
      evalEquals("new Object().isInstanceOf[Object]", "true")
      evalEquals("new Object().isInstanceOf[AnyRef]", "true")
      evalEquals("new Object().isInstanceOf[Any]", "true")
      evalStartsWith("new Object().isInstanceOf[AnyVal]", "class AnyVal cannot be used in runtime type tests")
      evalEquals("new Value(123).isInstanceOf[Value]", "true")
      evalStartsWith("new Value(123).isInstanceOf[AnyVal]", "class AnyVal cannot be used in runtime type tests")
      evalEquals("new Value(123).isInstanceOf[AnyRef]", "true")
      evalEquals("new Value(123).isInstanceOf[Int]", "false")
      evalStartsWith(""""123".isInstanceOf""", "isInstanceOf called without an explicit type argument")
      evalStartsWith("123.isInstanceOf", "isInstanceOf called without an explicit type argument")
      evalEquals(""""123".isInstanceOf[Alias]""", "true")
      evalStartsWith("123.isInstanceOf[Singleton]", "trait Singleton cannot be used in runtime type tests")
      evalStartsWith(""""123".isInstanceOf[Singleton]""", "trait Singleton cannot be used in runtime type tests")
    }
  }

  addFileWithBreakpoints("ArrayCreation.scala",
    s"""
       |object ArrayCreation {
       |  class Value(val n: Int) extends AnyVal {
       |    override def toString: String = n.toString
       |  }
       |
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
      evalEquals("""new Array(1)""", "[null]")
      evalEquals("""new Array[Int](1)""", "[0]")
      evalStartsWith("""new Array[Int]""", "Unspecified value parameters: _length: Int")
      evalStartsWith("""new Array""", "Unspecified value parameters: _length: Int")
      evalStartsWith("""new Array[Int]()""", "Unspecified value parameters: _length: Int")
      evalStartsWith("""new Array[String]()""", "Unspecified value parameters: _length: Int")
      evalStartsWith("""new Array[Int](1, 2)""", "Too many arguments for constructor Array(Int)")
      evalStartsWith("""new Array[String](1, 2, 3)""", "Too many arguments for constructor Array(Int)")
      evalEquals("""Array(1, 2, 3)""", "[1,2,3]")
      evalEquals("""Array(1, 2, 3).isInstanceOf[Array[Int]]""", "true")
      evalEquals("""Array(1, 2, 3).isInstanceOf[Array[java.lang.Integer]]""", "false")
      evalEquals("""Array[Int](1, 2, 3)""", "[1,2,3]")
      evalEquals("""Array[Int](1, 2, 3).isInstanceOf[Array[Int]]""", "true")
      evalEquals("""Array[Int](1, 2, 3).isInstanceOf[Array[java.lang.Integer]]""", "false")
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
      evalEquals("""new Array[Any](5)""", "[null,null,null,null,null]")
      evalEquals("""new Array[AnyRef](5)""", "[null,null,null,null,null]")
      evalEquals("""new Array[AnyVal](5)""", "[null,null,null,null,null]")
      evalEquals("""new Array[Null](5)""", "[null,null,null,null,null]")
      evalEquals("""new Array[Nothing](5)""", "[null,null,null,null,null]")
      evalEquals("""new Array[Singleton](5)""", "[null,null,null,null,null]")
      evalEquals("""new Array[Value](5)""", "[null,null,null,null,null]")
      evalEquals("""Array[Value]()""", "[]")
      evalEquals("""Array(new Value(5), new Value(6), new Value(7))""", "[5,6,7]")
    }

  addFileWithBreakpoints("ArrayMethods.scala",
    s"""
       |object ArrayMethods {
       |  class Value(val n: Int) extends AnyVal
       |
       |  val intArray: Array[Int] = Array(1, 2, 3)
       |  val stringArray: Array[String] = intArray.map(_.toString)
       |  val doubleArray: Array[Double] = intArray.map(_.toDouble)
       |  val objectArray: Array[AnyRef] = new Array(3)
       |  val multiArray: Array[Array[Int]] = Array.ofDim(2, 3)
       |  val valueArray: Array[Value] = new Array(3)
       |
       |  def main(args: Array[String]): Unit = {
       |    println()$bp
       |  }
       |}
      """.stripMargin
  )
  def testArrayMethods(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("intArray.clone()", "[1,2,3]")
      evalEquals("stringArray.clone()", "[1,2,3]")
      evalEquals("doubleArray.clone()", "[1.0,2.0,3.0]")
      evalEquals("objectArray.clone()", "[null,null,null]")
      evalEquals("multiArray.clone()", "[[0,0,0],[0,0,0]]")
      evalEquals("valueArray.clone()", "[null,null,null]")
      evalEquals("new Array[Any](1).clone()", "[null]")
      evalEquals("intArray.hashCode().toString ne null", "true")
      evalEquals("stringArray.hashCode().toString ne null", "true")
      evalEquals("doubleArray.hashCode().toString ne null", "true")
      evalEquals("objectArray.hashCode().toString ne null", "true")
      evalEquals("multiArray.hashCode().toString ne null", "true")
      evalEquals("valueArray.hashCode().toString ne null", "true")
      evalEquals("new Array[Any](1).hashCode().toString ne null", "true")
      evalStartsWith("intArray.toString", "[I")
      evalStartsWith("stringArray.toString", "[Ljava.lang.String;@")
      evalStartsWith("doubleArray.toString", "[D@")
      evalStartsWith("objectArray.toString", "[Ljava.lang.Object;@")
      evalStartsWith("multiArray.toString", "[[I@")
      evalStartsWith("valueArray.toString", "[LArrayMethods$Value;@")
      evalStartsWith("new Array[Any](1).toString", "[Ljava.lang.Object;@")
    }
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
       |  // Test repeated arguments
       |  def prependAll[A](list: List[A])(as: A*): List[A] =
       |    as.toList ++ list
       |
       |  class SimpleValue(val n: Int) extends AnyVal
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

      evalEquals("List(new SimpleValue(5), new SimpleValue(6), new SimpleValue(7))(0) == new SimpleValue(5)", "true")
      evalEquals("List(new SimpleValue(5), new SimpleValue(6), new SimpleValue(7))(1) == new SimpleValue(6)", "true")
      evalEquals("List(new SimpleValue(5), new SimpleValue(6), new SimpleValue(7))(2) == new SimpleValue(7)", "true")
      evalEquals("prependAll(List(new SimpleValue(5), new SimpleValue(6)))(new SimpleValue(1), new SimpleValue(2))(0) == new SimpleValue(1)", "true")
      evalEquals("prependAll(List(new SimpleValue(5), new SimpleValue(6)))(new SimpleValue(1), new SimpleValue(2))(1) == new SimpleValue(2)", "true")
      evalEquals("prependAll(List(new SimpleValue(5), new SimpleValue(6)))(new SimpleValue(1), new SimpleValue(2))(2) == new SimpleValue(5)", "true")
      evalEquals("prependAll(List(new SimpleValue(5), new SimpleValue(6)))(new SimpleValue(1), new SimpleValue(2))(3) == new SimpleValue(6)", "true")
    }

  addFileWithBreakpoints("ClassOf.scala",
    s"""object ClassOf {
       |  type StringAlias = String
       |  class Value(val v: Int) extends AnyVal
       |  class InnerClass
       |  object InnerObject {
       |    object DoublyNested
       |  }
       |
       |  type ValueAlias = Value
       |  type PrimitiveAlias = Int
       |  trait InnerTrait
       |
       |  def main(args: Array[String]): Unit = {
       |    println()$bp
       |  }
       |}""".stripMargin)
  def testClassOf(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("classOf[Any]", "class java.lang.Object")
      evalEquals("classOf[AnyRef]", "class java.lang.Object")
      evalEquals("classOf[AnyVal]", "class java.lang.Object")
      evalEquals("classOf[Singleton]", "class java.lang.Object")
      evalEquals("classOf[Null]", "class scala.runtime.Null$")
      evalEquals("classOf[Nothing]", "class scala.runtime.Nothing$")
      evalEquals("classOf[Unit]", "void")
      evalEquals("classOf[Boolean]", "boolean")
      evalEquals("classOf[Byte]", "byte")
      evalEquals("classOf[Char]", "char")
      evalEquals("classOf[Double]", "double")
      evalEquals("classOf[Float]", "float")
      evalEquals("classOf[Int]", "int")
      evalEquals("classOf[Long]", "long")
      evalEquals("classOf[Short]", "short")
      evalStartsWith("classOf[123]", "Literal type (123 : Int) is not a class type")
      evalStartsWith("""classOf["abc"]""", """Literal type ("abc" : String) is not a class type""")
      evalEquals("classOf[String]", "class java.lang.String")
      evalEquals("classOf[List[Int]]", "class scala.collection.immutable.List")
      evalEquals("classOf[StringAlias]", "class java.lang.String")
      evalEquals("classOf[ClassOf.type]", "class ClassOf$")
      evalEquals("classOf[Value]", "class ClassOf$Value")
      evalEquals("classOf[ValueAlias]", "class ClassOf$Value")
      evalEquals("classOf[InnerClass]", "class ClassOf$InnerClass")
      evalEquals("classOf[InnerObject.type]", "class ClassOf$InnerObject$")
      evalEquals("classOf[PrimitiveAlias]", "int")
      evalEquals("classOf[InnerTrait]", "interface ClassOf$InnerTrait")
      evalEquals("classOf[InnerObject.DoublyNested.type]", "class ClassOf$InnerObject$DoublyNested$")
      evalEquals("classOf[java.io.Serializable]", "interface java.io.Serializable")
    }
  }

  addFileWithBreakpoints("While.scala",
    s"""object While {
       |  def main(args: Array[String]): Unit = {
       |    println()$bp
       |  }
       |}""".stripMargin)
  def testWhile(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals(
        """val array = new Array[Int](5)
          |var i = 0
          |while (i < array.length) {
          |  array(i) = i
          |  i += 1
          |}
          |array
          |""".stripMargin, "[0,1,2,3,4]")

      evalEquals(
        """val array = new Array[Int](5)
          |var i = 0
          |while ({
          |  array(i) = i
          |  i += 1
          |  i < array.length
          |}) {}
          |array
          |""".stripMargin, "[0,1,2,3,4]")
    }
  }

  addFileWithBreakpoints("DoWhile.scala",
    s"""object DoWhile {
       |  def main(args: Array[String]): Unit = {
       |    println()$bp
       |  }
       |}""".stripMargin)
  def testDoWhile(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals(
        """val array = new Array[Int](5)
          |var i = 0
          |do {
          |  array(i) = i
          |  i += 1
          |} while (i < array.length)
          |array
          |""".stripMargin, "[0,1,2,3,4]")
    }
  }
}