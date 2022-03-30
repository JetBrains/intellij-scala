package org.jetbrains.plugins.scala
package debugger
package evaluateExpression

import org.jetbrains.plugins.scala.base.ScalaSdkOwner
import org.junit.experimental.categories.Category

@Category(Array(classOf[DebuggerTests]))
class CodeFragmentEvaluationTest_2_11 extends CodeFragmentEvaluationTestBase with ScalaSdkOwner {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_11
}

@Category(Array(classOf[DebuggerTests]))
class CodeFragmentEvaluationTest_2_12 extends CodeFragmentEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_12
}

@Category(Array(classOf[DebuggerTests]))
class CodeFragmentEvaluationTest_2_13 extends CodeFragmentEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_13
}

@Category(Array(classOf[DebuggerTests]))
class CodeFragmentEvaluationTest_3_0 extends CodeFragmentEvaluationTest_2_13 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_0

  addFileWithBreakpoints("Scala3Syntax.scala",
    s"""package test
       |
       |@main
       |def scala3Syntax(): Unit =
       |  val name = "world"
       |  println(s"hello, $$name") $bp
       |""".stripMargin)

  def testScala3Syntax(): Unit = {
    evaluateCodeFragments("test/scala3Syntax",
      "if true then 42 else 0" -> "42",
      """if true then
        |  println(true)
        |  name.length
        |else
        |  0
        |""" -> "5"
    )
  }
}

@Category(Array(classOf[DebuggerTests]))
class CodeFragmentEvaluationTest_3_1 extends CodeFragmentEvaluationTest_3_0 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_1
}

@Category(Array(classOf[DebuggerTests]))
abstract class CodeFragmentEvaluationTestBase extends ScalaDebuggerTestCase with ScalaSdkOwner {

  addFileWithBreakpoints("CodeFragments.scala",
    s"""package test
       |
       |final case class One(a: Int)
       |
       |final case class Two(a: String, b: Int)
       |
       |object MyUnapply {
       |  def unapply(s: String): Option[String] = s.split(" ") match {
       |    case Array(first, _) => Some(first)
       |    case _ => None
       |  }
       |}
       |
       |class MyProduct(a: String, b: Int) extends Product with Serializable {
       |  override def productArity: Int = 2
       |
       |  override def productElement(n: Int): Any = n match {
       |    case 0 => a
       |    case 1 => b
       |    case _ => throw new IndexOutOfBoundsException(n)
       |  }
       |
       |  override def canEqual(that: Any): Boolean = that.isInstanceOf[MyProduct]
       |}
       |
       |object MyProduct {
       |  def unapply(p: MyProduct): Some[(String, Int)] =
       |    Some((p.productElement(0).asInstanceOf[String], p.productElement(1).asInstanceOf[Int]))
       |}
       |
       |object CodeFragments {
       |  var n = 0
       |  def main(args: Array[String]): Unit = {
       |    val str = "some string"
       |    println()$bp
       |  }
       |}
      """.stripMargin.trim()
  )

  def testCodeFragments(): Unit = {
    evaluateCodeFragments("test.CodeFragments",
      """1 + 1
        |2 + 2
        |3 + 3""" -> "6",

      """n = 0
        |n += 1
        |n = n + 2
        |n""" -> "3",

      """val words = str.split(' ')
        |words(0)
        |""" -> "some",

      """val words = str.split(' ')
        |words(1)
        |""" -> "string",

      """val str = "other string"
        |val words = str.split(' ')
        |words(0)
        |""" -> "other",

      """val Seq(first, second) = str.split(' ').toSeq
        |(first, second)
        |""" -> "(some,string)",

      """val words, Seq(first, second) = str.split(' ').toSeq
        |words(0)
        |""" -> "some",

      """val Array(first, second) = str.split(" ")
        |(first, second)""" -> "(some,string)",

      """val List(first, second) = str.split(" ").toList
        |(first, second)""" -> "(some,string)",

      """val Vector(first, second) = str.split(" ").toVector
        |(first, second)""" -> "(some,string)",

      """var i = 0
        |i += 25
        |i""" -> "25",

      """var res = 0
        |val z = 1
        |if (true) {
        |  val z = 2
        |  res = z
        |}
        |res""" -> "2",

      """var res = 0
        |val z = 1
        |if (true) {
        |  val z = 2
        |  res = z
        |}
        |res = z
        |res""" -> "1",

      """n = 0; val x = n + 1; x""" -> "1",

      """var sum = 0
        |var i = 0
        |while(i <= 5) {
        |  sum += i
        |  i += 1
        |}
        |sum""" -> "15",

      """val one = One(1)
        |val One(a) = one
        |a""" -> "1",

      """val two = Two("a", 2)
        |val Two(a, b) = two
        |(a, b)""" -> "(a,2)",

      """val MyUnapply(name) = "Name Surname"
        |name""".stripMargin -> "Name",

      """val p = new MyProduct("string", 25)
        |val MyProduct(a, b) = p
        |(a, b)""" -> "(string,25)"
    )
  }
}
