package org.jetbrains.plugins.scala
package debugger
package evaluation

class CodeFragmentEvaluationTest_2_11 extends CodeFragmentEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_11
}

class CodeFragmentEvaluationTest_2_12 extends CodeFragmentEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_12
}

class CodeFragmentEvaluationTest_2_13 extends CodeFragmentEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13
}

class CodeFragmentEvaluationTest_3 extends CodeFragmentEvaluationTest_2_13 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  addSourceFile("Scala3Syntax.scala",
    s"""package test
       |
       |@main
       |def scala3Syntax(): Unit =
       |  val name = "world"
       |  println(s"hello, $$name") $breakpoint
       |""".stripMargin.trim)

  def testScala3Syntax(): Unit = {
    expressionEvaluationTest("test.scala3Syntax") { implicit ctx =>
      evalEquals("if true then 42 else 0", "42")

      evalEquals(
        """if true then
          |  println(true)
          |  name.length
          |else
          |  0""".stripMargin.trim, "5")
    }
  }
}

class CodeFragmentEvaluationTest_3_RC extends CodeFragmentEvaluationTest_3 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_RC
}

abstract class CodeFragmentEvaluationTestBase extends ExpressionEvaluationTestBase {
  addSourceFile("CodeFragments.scala",
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
       |    println() $breakpoint
       |  }
       |}
      """.stripMargin.trim
  )

  def testCodeFragments(): Unit = {
    expressionEvaluationTest("test.CodeFragments") { implicit ctx =>
      evalEquals(
        """1 + 1
          |2 + 2
          |3 + 3""".stripMargin.trim, "6")

      evalEquals(
        """n = 0
          |n += 1
          |n = n + 2
          |n""".stripMargin.trim, "3")

      evalEquals(
        """val words = str.split(' ')
          |words(0)""".stripMargin.trim, "some")

      evalEquals(
        """val words = str.split(' ')
          |words(1)""".stripMargin.trim, "string")

      evalEquals(
        """val str = "other string"
          |val words = str.split(' ')
          |words(0)""".stripMargin.trim, "other")

      evalEquals(
        """val str = "other string"
          |val words = str.split(' ')
          |words(1)""".stripMargin.trim, "string")

      evalEquals(
        """val Seq(first, second) = str.split(' ').toSeq
          |(first, second)""".stripMargin.trim, "(some,string)")

      evalEquals(
        """val words, Seq(first, second) = str.split(' ').toSeq
          |words(0)""".stripMargin.trim, "some")

      evalEquals(
        """val Array(first, second) = str.split(" ")
          |(first, second)""".stripMargin.trim, "(some,string)")

      evalEquals(
        """val List(first, second) = str.split(" ").toList
          |(first, second)""".stripMargin.trim, "(some,string)")

      evalEquals(
        """val Vector(first, second) = str.split(" ").toVector
          |(first, second)""".stripMargin.trim, "(some,string)")

      evalEquals(
        """var i = 0
          |i += 25
          |i""".stripMargin.trim, "25")

      evalEquals(
        """var res = 0
          |val z = 1
          |if (true) {
          |  val z = 2
          |  res = z
          |}
          |res""".stripMargin.trim, "2")

      evalEquals(
        """var res = 0
          |val z = 1
          |if (true) {
          |  val z = 2
          |  res = z
          |}
          |res = z
          |res""".stripMargin.trim, "1")

      evalEquals("""n = 0; val x = n + 1; x""", "1")

      evalEquals(
        """var sum = 0
          |var i = 0
          |while(i <= 5) {
          |  sum += i
          |  i += 1
          |}
          |sum""".stripMargin.trim, "15")

      evalEquals(
        """val one = One(1)
          |val One(a) = one
          |a""".stripMargin.trim, "1")

      evalEquals(
        """val two = Two("a", 2)
          |val Two(a, b) = two
          |(a, b)""".stripMargin.trim, "(a,2)")

      evalEquals(
        """val MyUnapply(name) = "Name Surname"
          |name""".stripMargin.trim, "Name")

      evalEquals(
        """val p = new MyProduct("string", 25)
          |val MyProduct(a, b) = p
          |(a, b)""".stripMargin.trim, "(string,25)")
    }
  }
}
