package org.jetbrains.plugins.scala
package codeInspection.typeChecking

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.codeInspection.{InspectionBundle, ScalaInspectionTestBase}

/**
 * Nikolay.Tropin
 * 9/26/13
 */
class ComparingUnrelatedTypesInspectionTest extends ScalaInspectionTestBase {

  import EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[ComparingUnrelatedTypesInspection]

  override protected val description: String =
    ComparingUnrelatedTypesInspection.inspectionName

  def testWeakConformance() {
    val text1 = s"""val a = 0
                 |val b: Short = 1
                 |${START}b == a$END"""
    checkTextHasNoUnrelatedTypeErrors(text1)

    val text2 = s"""val a = 0
                  |val b = 1.0
                  |${START}b != a$END"""
    checkTextHasNoUnrelatedTypeErrors(text2)

    val text3 = s"""val a = 0.0
                  |val b: Byte = 100
                  |${START}a == b$END"""
    checkTextHasNoUnrelatedTypeErrors(text3)

    val text4 = s"${START}1 == 1.0$END"
    checkTextHasNoUnrelatedTypeErrors(text4)
  }

  def testValueTypes() {
    val text1 = s"""val a = true
                 |val b = 1
                 |${START}b == a$END"""
    checkTextHasError(text1, errorDescriptionForTypes("Int", "Boolean"))

    val text2 = s"""val a = true
                 |val b = 0.0
                 |${START}a != b$END"""
    checkTextHasError(text2, errorDescriptionForTypes("Boolean", "Double"))

    val text3 = s"${START}true != 0$END"
    checkTextHasError(text3, errorDescriptionForTypes("Boolean", "Int"))

    val text4: String = s"${START}1.isInstanceOf[Boolean]$END"
    checkTextHasError(text4, errorDescriptionForTypes("Int", "Boolean"))

  }

  def testString() {
    val text1 = s"""val a = "a"
                 |val b = Array('a')
                 |${START}b == a$END"""
    checkTextHasError(text1, errorDescriptionForTypes("Array[Char]", "String"))

    val text2 = s"""val a = "0"
                 |val b = 0
                 |${START}a == b$END"""
    checkTextHasError(text2, errorDescriptionForTypes("String", "Int"))

    val text3 = s"""val s = "s"
                  |${START}s == 's'$END"""
    checkTextHasError(text3, errorDescriptionForTypes("String", "Char"))

    val text4 = s"""val a = "a"
                  |val b: CharSequence = null
                  |${START}b != a$END"""
    checkTextHasNoUnrelatedTypeErrors(text4)
  }

  def testInheritors() {
    val text1 = s"""val a = scala.collection.Iterable(1)
                 |val b = List(0)
                 |${START}b == a$END"""
    checkTextHasNoUnrelatedTypeErrors(text1)

    val text2 = s"""case class A(i: Int)
                   |final class B extends A(1)
                   |val a: A = A(0)
                   |val b: B = new B
                   |${START}a == b$END"""
    checkTextHasNoUnrelatedTypeErrors(text2)

    val text3 = """trait A
                  |object B extends A
                  |B.isInstanceOf[A]"""
    checkTextHasNoUnrelatedTypeErrors(text3)
  }

  def testFinal() {
    val text1 = s"""case class A(i: Int)
                   |class B extends A(1)
                   |val a: A = A(0)
                   |val b: B = new B
                   |${START}a == b$END"""
    checkTextHasNoUnrelatedTypeErrors(text1)

    val text2 = s"""final class A extends Serializable
                  |final class B extends Serializable
                  |val a: A = new A
                  |val b: B = new B
                  |${START}a == b$END"""
    checkTextHasError(text2, errorDescriptionForTypes("A", "B"))

    val text3 = s"""final class A extends Serializable
                   |final class B extends Serializable
                   |val a: A = new A
                   |${START}a.isInstanceOf[B]$END"""
    checkTextHasError(text3, errorDescriptionForTypes("A", "B"))
  }

  def testTraits() {
    val text1 = s"""trait A
                  |trait B
                  |val a: A = _
                  |val b: B = _
                  |${START}a == b$END"""
    checkTextHasNoUnrelatedTypeErrors(text1)
  }

  def testObject() {
    val text1 = s"""trait A
                  |object B extends A
                  |val a: A = _
                  |${START}a == B$END"""
    checkTextHasNoUnrelatedTypeErrors(text1)

    val text2 = s"""trait A
                  |object B extends A
                  |class C extends A
                  |val c = new C
                  |${START}c == B$END"""
    checkTextHasError(text2, errorDescriptionForTypes("C", "B.type"))

    val text3 = s"""trait A
                  |object B extends A
                  |class C extends A
                  |val c: A = new C
                  |${START}c != B$END"""
    checkTextHasNoUnrelatedTypeErrors(text3)
  }

  def testBoxedTypes() {
    val text1 = """val i = new java.lang.Integer(0)
                  |i == 100"""
    checkTextHasNoUnrelatedTypeErrors(text1)

    val text2 = """val b = new java.lang.Boolean(false)
                  |b equals true"""
    checkTextHasNoUnrelatedTypeErrors(text2)

    val text3 = "def test(i: Integer) = if (i == null) \"foo\" else \"bar\""
    checkTextHasNoUnrelatedTypeErrors(text3)
  }

  def testExistential(): Unit = {
    checkTextHasNoUnrelatedTypeErrors("Seq(1).isInstanceOf[List[_])")
    checkTextHasError(s"${START}Some(1).isInstanceOf[List[_]]$END", errorDescriptionForTypes("Some[Int]", "List[_]"))
    checkTextHasNoUnrelatedTypeErrors("def foo(x: Some[_]) { x == Some(1) }")
    checkTextHasError(s"def foo(x: Some[_]) { ${START}x == Seq(1)$END }", errorDescriptionForTypes("Some[_]", "Seq[Int]"))
  }

  def testNumeric(): Unit = {
    checkTextHasNoUnrelatedTypeErrors("BigInt(1) == 1")
    checkTextHasNoUnrelatedTypeErrors("BigInt(1) == 1L")
    checkTextHasNoUnrelatedTypeErrors("BigInt(1) == new java.lang.Integer(1)")
    checkTextHasError(s"${START}BigInt(1) == true$END", errorDescriptionForTypes("BigInt", "Boolean"))
    checkTextHasError(s"${START}BigInt(1) == 1.toString$END", errorDescriptionForTypes("BigInt", "String"))
  }

  def testTypeAlias(): Unit = {
    checkTextHasNoUnrelatedTypeErrors(
      """
        |object A {
        |  type Coord = Float
        |  def isZero(n: Coord): Boolean = {
        |    n == 0
        |  }
        |}
      """.stripMargin)

    checkTextHasError(
      s"""
        |object A {
        |  type Coord = String
        |  def isZero(n: Coord): Boolean = {
        |    ${START}n == 0$END
        |  }
        |}
      """.stripMargin, errorDescriptionForTypes("A.Coord", "Int"))

    checkTextHasNoUnrelatedTypeErrors(
      """
        |trait A {
        |  type Coord
        |
        |  def isZero(n: Coord): Boolean = {
        |    n == 0
        |  }
        |}
      """.stripMargin)
  }

  def testOverridenMethods(): Unit = {
    checkTextHasNoUnrelatedTypeErrors(
      """
        |case class Dummy(v: Int) {
        |  def ==(value: Int): String = v + " == " + value
        |  def !=(value: Int): Boolean = v != value
        |}
        |
        |object Test {
        |  val a: String = Dummy(5) == 10
        |  val b: Boolean = Dummy(5) != 10
        |}""".stripMargin)

    checkTextHasError(
      s"""
        |case class Dummy(v: Int) {
        |  def ==(value: Int): String = v + " == " + value
        |  def !=(value: Int): Boolean = v != value
        |}
        |
        |object Test {
        |  val b: Boolean = ${START}Dummy(5) eq 10$END
        |}""".stripMargin, errorDescriptionForTypes("Dummy", "Int"))

  }

  def testOverridenWithImplicitParam(): Unit = {
    checkTextHasError(
      s"""
        |class Store(val foo: Int, val bar: String)
        |trait Binder[T] {
        |  def get(implicit store: Store): T
        |  def ==(other: Binder[T])(implicit store: Store) = get == other.get
        |  def ==(other: T)(implicit store: Store) = get == other
        |}
        |class FooBinder extends Binder[Int] {
        |  def get(implicit store: Store) = store.foo
        |}
        |class BarBinder extends Binder[String] {
        |  def get(implicit store: Store) = store.bar
        |}
        |
        |val fooBinder = new FooBinder
        |val barBinder = new BarBinder
        |
        |{
        |  implicit val store = new Store(12, ":)")
        |  (fooBinder == 12, fooBinder == 3, ${START}fooBinder == ":)"$END, barBinder == ":)") // (true, false, false, true)
        |}
      """.stripMargin, errorDescriptionForTypes("FooBinder", "String")
    )

  }

  def testOverridenEquals(): Unit = {
    checkTextHasError(
      s"""
         |case class Dummy(v: Int) {
         |  override def equals(other: Any): Boolean = other match {
         |    case Dummy(o) => o == v
         |    case _ => false
         |  }
         |}
         |
         |object Test {
         |  val b: Boolean = ${START}Dummy(5) equals 10$END
         |}""".stripMargin, errorDescriptionForTypes("Dummy", "Int"))

    checkTextHasError(
      s"""
         |case class Dummy(v: Int) {
         |  override def equals(other: Any): Boolean = other match {
         |    case Dummy(o) => o == v
         |    case _ => false
         |  }
         |}
         |
           |object Test {
         |  val b: Boolean = ${START}Dummy(5) == 10$END
         |}""".stripMargin, errorDescriptionForTypes("Dummy", "Int"))
  }

  def testSameNameTypes(): Unit = {
    checkTextHasError(
      s"""
         |package abc {
         |  class Dummy
         |}
         |
         |package cde {
         |  class Dummy
         |}
         |
         |object Test {
         |  val d1 = new abc.Dummy
         |  val d2 = new cde.Dummy
         |  ${START}d1 == d2$END
         |}
       """.stripMargin, errorDescriptionForTypes("abc.Dummy", "cde.Dummy"))
  }

  private def checkTextHasNoUnrelatedTypeErrors(text: String) = {
    checkTextHasNoErrors(text,desc => desc != null && desc.toLowerCase.contains("unrelated"))
  }

  private def errorDescriptionForTypes(firstType: String, secondType: String): String = {
    InspectionBundle.message("comparing.unrelated.types.hint", firstType, secondType)
  }
}