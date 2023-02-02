package org.jetbrains.plugins.scala.codeInsight.generation.actions

import com.intellij.lang.LanguageCodeInsightActionHandler
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class GenerateEqualsTest extends ScalaGenerateTestBase {

  import CodeInsightTestFixture.CARET_MARKER

  override protected val handler: LanguageCodeInsightActionHandler =
    new ScalaGenerateEqualsAction.Handler

  def testFindAllFields(): Unit = {
    val text =
      s"""class A (i: Int, val j: Int) {
         |  val x = 0$CARET_MARKER
         |  var y = 0
         |  private val z = 0
         |}""".stripMargin
    val result =
      """class A (i: Int, val j: Int) {
        |  val x = 0
        |  var y = 0
        |  private val z = 0
        |
        |  def canEqual(other: Any): Boolean = other.isInstanceOf[A]
        |
        |  override def equals(other: Any): Boolean = other match {
        |    case that: A =>
        |      that.canEqual(this) &&
        |        x == that.x &&
        |        y == that.y &&
        |        z == that.z &&
        |        j == that.j
        |    case _ => false
        |  }
        |
        |  override def hashCode(): Int = {
        |    val state = Seq(x, z, j)
        |    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
        |  }
        |}""".stripMargin
    performTest(text, result)
  }

  def testInFinalClass(): Unit = {
    val text =
      s"""final class$CARET_MARKER A (i: Int, val j: Int) {
         |  private val z = 0
         |}""".stripMargin
    val result =
      """final class A (i: Int, val j: Int) {
        |  private val z = 0
        |
        |  override def equals(other: Any): Boolean = other match {
        |    case that: A =>
        |      z == that.z &&
        |        j == that.j
        |    case _ => false
        |  }
        |
        |  override def hashCode(): Int = {
        |    val state = Seq(z, j)
        |    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
        |  }
        |}""".stripMargin
    performTest(text, result)
  }

  def testInAbstract(): Unit = {
    val text =
      s"""abstract class A (i: Int, val j: Int) extends Set[Int] {
         |  private val z = 0
         |
         |$CARET_MARKER}""".stripMargin
    val result =
      s"""abstract class A (i: Int, val j: Int) extends Set[Int] {
         |  private val z = 0
         |
         |  override def canEqual(other: Any): Boolean = other.isInstanceOf[A]
         |
         |  override def equals(other: Any): Boolean = other match {
         |    case that: A =>
         |      super.equals(that) &&
         |        that.canEqual(this) &&
         |        z == that.z &&
         |        j == that.j
         |    case _ => false
         |  }
         |
         |  override def hashCode(): Int = {
         |    val state = Seq(super.hashCode(), z, j)
         |    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
         |  }
         |}""".stripMargin
    performTest(text, result)
  }

  def testInInheritor(): Unit = {
    val text =
      s"""class A {
         |  val a = 0
         |
         |  def canEqual(other: Any): Boolean = other.isInstanceOf[A]
         |
         |  override def equals(other: Any): Boolean = other match {
         |    case that: A =>
         |      that.canEqual(this) &&
         |        a == that.a
         |    case _ => false
         |  }
         |
         |  override def hashCode(): Int = {
         |    val state = Seq(a)
         |    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
         |  }
         |}
         |
         |class B (i: Int, val j: Int) extends A {
         |  val z = 0$CARET_MARKER
         |}""".stripMargin
    val result =
      """class A {
        |  val a = 0
        |
        |  def canEqual(other: Any): Boolean = other.isInstanceOf[A]
        |
        |  override def equals(other: Any): Boolean = other match {
        |    case that: A =>
        |      that.canEqual(this) &&
        |        a == that.a
        |    case _ => false
        |  }
        |
        |  override def hashCode(): Int = {
        |    val state = Seq(a)
        |    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
        |  }
        |}
        |
        |class B (i: Int, val j: Int) extends A {
        |  val z = 0
        |
        |  override def canEqual(other: Any): Boolean = other.isInstanceOf[B]
        |
        |  override def equals(other: Any): Boolean = other match {
        |    case that: B =>
        |      super.equals(that) &&
        |        that.canEqual(this) &&
        |        z == that.z &&
        |        j == that.j
        |    case _ => false
        |  }
        |
        |  override def hashCode(): Int = {
        |    val state = Seq(super.hashCode(), z, j)
        |    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
        |  }
        |}""".stripMargin
    performTest(text, result)
  }

  def testInheritsMethodsFromJavaLangObject(): Unit = {
    val text =
      s"""class A {
         |  val a = 0
         |}
         |
         |class B (i: Int, val j: Int) extends A {
         |  val z = 0$CARET_MARKER
         |}""".stripMargin
    val result =
      """class A {
        |  val a = 0
        |}
        |
        |class B (i: Int, val j: Int) extends A {
        |  val z = 0
        |
        |  def canEqual(other: Any): Boolean = other.isInstanceOf[B]
        |
        |  override def equals(other: Any): Boolean = other match {
        |    case that: B =>
        |      that.canEqual(this) &&
        |        z == that.z &&
        |        j == that.j
        |    case _ => false
        |  }
        |
        |  override def hashCode(): Int = {
        |    val state = Seq(z, j)
        |    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
        |  }
        |}""".stripMargin
    performTest(text, result)
  }

  def testFieldNamesClashWithGeneratedValNames(): Unit = {
    val text =
      s"""class Foo(
         |  val value: Int,
         |  val state: Int,
         |  val that: Int,
         |  val other: Int
         |) {
         |  $CARET_MARKER
         |}""".stripMargin
    val result =
      """class Foo(
        |  val value: Int,
        |  val state: Int,
        |  val that: Int,
        |  val other: Int
        |) {
        |
        |  def canEqual(other1: Any): Boolean = other1.isInstanceOf[Foo]
        |
        |  override def equals(other1: Any): Boolean = other1 match {
        |    case that1: Foo =>
        |      that1.canEqual(this) &&
        |        value == that1.value &&
        |        state == that1.state &&
        |        that == that1.that &&
        |        other == that1.other
        |    case _ => false
        |  }
        |
        |  override def hashCode(): Int = {
        |    val state1 = Seq(value, state, that, other)
        |    state1.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
        |  }
        |}""".stripMargin
    performTest(text, result)
  }
}

class GenerateEqualsTest_Scala3 extends ScalaGenerateTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  override protected val handler: LanguageCodeInsightActionHandler =
    new ScalaGenerateEqualsAction.Handler

  def testFindAllFields(): Unit = {
    val text =
      s"""class A (i: Int, val j: Int):
         |  val x = 0$CARET
         |  var y = 0
         |  private val z = 0
         |""".stripMargin
    val result =
      """class A (i: Int, val j: Int):
        |  val x = 0
        |  var y = 0
        |
        |  def canEqual(other: Any): Boolean = other.isInstanceOf[A]
        |
        |  override def equals(other: Any): Boolean = other match
        |    case that: A =>
        |      that.canEqual(this) &&
        |        x == that.x &&
        |        y == that.y &&
        |        z == that.z &&
        |        j == that.j
        |    case _ => false
        |
        |  override def hashCode(): Int =
        |    val state = Seq(x, z, j)
        |    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
        |
        |  private val z = 0
        |""".stripMargin
    performTest(text, result)
  }

  def testInFinalClass(): Unit = {
    val text =
      s"""final class$CARET A (i: Int, val j: Int):
         |  private val z = 0
         |""".stripMargin
    val result =
      """final class A (i: Int, val j: Int):
        |  override def equals(other: Any): Boolean = other match
        |    case that: A =>
        |      z == that.z &&
        |        j == that.j
        |    case _ => false
        |
        |  override def hashCode(): Int =
        |    val state = Seq(z, j)
        |    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
        |
        |  private val z = 0
        |""".stripMargin
    performTest(text, result)
  }

  def testInAbstract(): Unit = {
    val text =
      s"""abstract class A (i: Int, val j: Int) extends Set[Int]:
         |  private val z = 0
         |
         |${CARET}end A""".stripMargin
    val result =
      s"""abstract class A (i: Int, val j: Int) extends Set[Int]:
         |  private val z = 0
         |
         |  override def canEqual(other: Any): Boolean = other.isInstanceOf[A]
         |
         |  override def equals(other: Any): Boolean = other match
         |    case that: A =>
         |      super.equals(that) &&
         |        that.canEqual(this) &&
         |        z == that.z &&
         |        j == that.j
         |    case _ => false
         |
         |  override def hashCode(): Int =
         |    val state = Seq(super.hashCode(), z, j)
         |    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
         |end A""".stripMargin
    performTest(text, result)
  }

  def testInInheritor(): Unit = {
    val text =
      s"""class A:
         |  val a = 0
         |
         |  def canEqual(other: Any): Boolean = other.isInstanceOf[A]
         |
         |  override def equals(other: Any): Boolean = other match
         |    case that: A =>
         |      that.canEqual(this) &&
         |        a == that.a
         |    case _ => false
         |
         |  override def hashCode(): Int =
         |    val state = Seq(a)
         |    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
         |end A
         |
         |class B (i: Int, val j: Int) extends A:
         |  val z = 0$CARET
         |end B""".stripMargin
    val result =
      """class A:
        |  val a = 0
        |
        |  def canEqual(other: Any): Boolean = other.isInstanceOf[A]
        |
        |  override def equals(other: Any): Boolean = other match
        |    case that: A =>
        |      that.canEqual(this) &&
        |        a == that.a
        |    case _ => false
        |
        |  override def hashCode(): Int =
        |    val state = Seq(a)
        |    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
        |end A
        |
        |class B (i: Int, val j: Int) extends A:
        |  val z = 0
        |
        |  override def canEqual(other: Any): Boolean = other.isInstanceOf[B]
        |
        |  override def equals(other: Any): Boolean = other match
        |    case that: B =>
        |      super.equals(that) &&
        |        that.canEqual(this) &&
        |        z == that.z &&
        |        j == that.j
        |    case _ => false
        |
        |  override def hashCode(): Int =
        |    val state = Seq(super.hashCode(), z, j)
        |    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
        |end B""".stripMargin
    performTest(text, result)
  }

  def testInheritsMethodsFromJavaLangObject(): Unit = {
    val text =
      s"""class A:
         |  val a = 0
         |
         |class$CARET B (i: Int, val j: Int) extends A:
         |  val z = 0
         |""".stripMargin
    val result =
      """class A:
        |  val a = 0
        |
        |class B (i: Int, val j: Int) extends A:
        |  def canEqual(other: Any): Boolean = other.isInstanceOf[B]
        |
        |  override def equals(other: Any): Boolean = other match
        |    case that: B =>
        |      that.canEqual(this) &&
        |        z == that.z &&
        |        j == that.j
        |    case _ => false
        |
        |  override def hashCode(): Int =
        |    val state = Seq(z, j)
        |    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
        |
        |  val z = 0
        |""".stripMargin
    performTest(text, result)
  }
}
