package org.jetbrains.plugins.scala
package codeInspection
package typeChecking

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}

/**
  * Nikolay.Tropin
  * 9/26/13
  */
abstract class ComparingUnrelatedTypesInspectionTest extends ScalaInspectionTestBase {

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[ComparingUnrelatedTypesInspection]
}

class Test1 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    InspectionBundle.message("comparing.unrelated.types.hint", "Short", "Int")

  def testWeakConformance(): Unit = checkTextHasNoErrors(
    s"""val a = 0
       |val b: Short = 1
       |${START}b == a$END
       """.stripMargin
  )
}

class Test2 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    InspectionBundle.message("comparing.unrelated.types.hint", "Double", "Int")

  def testWeakConformance(): Unit = checkTextHasNoErrors(
    s"""val a = 0
       |val b = 1.0
       |${START}b != a$END
       """.stripMargin
  )
}

class Test3 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    InspectionBundle.message("comparing.unrelated.types.hint", "Double", "Byte")

  def testWeakConformance(): Unit = checkTextHasNoErrors(
    s"""val a = 0.0
       |val b: Byte = 100
       |${START}a == b$END
       """.stripMargin
  )
}

class Test4 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    InspectionBundle.message("comparing.unrelated.types.hint", "Int", "Double")

  def testWeakConformance(): Unit = checkTextHasNoErrors(
    s"${START}1 == 1.0$END"
  )
}

class Test5 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    InspectionBundle.message("comparing.unrelated.types.hint", "Int", "Boolean")

  def testValueType(): Unit = checkTextHasError(
    s"""val a = true
       |val b = 1
       |${START}b == a$END
       """.stripMargin
  )

  def testInstanceOf(): Unit = checkTextHasError(
    s"${START}1.isInstanceOf[Boolean]$END"
  )
}

class Test6 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    InspectionBundle.message("comparing.unrelated.types.hint", "Boolean", "Double")

  def testValueType(): Unit = checkTextHasError(
    s"""val a = true
       |val b = 0.0
       |${START}a != b$END
       """.stripMargin
  )
}

class Test7 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    InspectionBundle.message("comparing.unrelated.types.hint", "Boolean", "Int")

  def testValueType(): Unit = checkTextHasError(
    s"${START}true != 0$END"
  )
}

class Test8 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    InspectionBundle.message("comparing.unrelated.types.hint", "Array[Char]", "String")

  def testString(): Unit = checkTextHasError(
    s"""val a = "a"
       |val b = Array('a')
       |${START}b == a$END
       """.stripMargin
  )
}

class Test9 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    InspectionBundle.message("comparing.unrelated.types.hint", "String", "Int")

  def testString(): Unit = checkTextHasError(
    s"""val a = "0"
       |val b = 0
       |${START}a == b$END
       """.stripMargin
  )
}

class Test10 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    InspectionBundle.message("comparing.unrelated.types.hint", "String", "Char")

  def testString(): Unit = checkTextHasError(
    s"""val s = "s"
       |${START}s == 's'$END
       """.stripMargin
  )
}

class Test11 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    InspectionBundle.message("comparing.unrelated.types.hint", "CharSequence", "String")

  def testString(): Unit = checkTextHasNoErrors(
    s"""val a = "a"
       |val b: CharSequence = null
       |${START}b != a$END
      """.stripMargin
  )
}

class Test12 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    InspectionBundle.message("comparing.unrelated.types.hint", "scala.collection.Iterable", "scala.collection.List")

  def testInheritors(): Unit = checkTextHasNoErrors(
    s"""val a = scala.collection.Iterable(1)
       |val b = List(0)
       |${START}b == a$END
       """.stripMargin
  )
}

class Test13 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    InspectionBundle.message("comparing.unrelated.types.hint", "A", "B")

  def testInheritors(): Unit = checkTextHasNoErrors(
    s"""case class A(i: Int)
       |final class B extends A(1)
       |val a: A = A(0)
       |val b: B = new B
       |${START}a == b$END
       """.stripMargin
  )

  def testFinal(): Unit = checkTextHasError(
    s"""final class A extends Serializable
       |final class B extends Serializable
       |val a: A = new A
       |val b: B = new B
       |${START}a == b$END
      """.stripMargin
  )

  def testInstanceOf(): Unit = checkTextHasError(
    s"""final class A extends Serializable
       |final class B extends Serializable
       |val a: A = new A
       |${START}a.isInstanceOf[B]$END
      """
  )

  def testTraits(): Unit = checkTextHasNoErrors(
    s"""trait A
       |trait B
       |val a: A = _
       |val b: B = _
       |${START}a == b$END
      """.stripMargin
  )
}

class Test14 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    InspectionBundle.message("comparing.unrelated.types.hint", "B", "A")

  def testInheritors(): Unit = checkTextHasNoErrors(
    s"""trait A
       |object B extends A
       |${START}B.isInstanceOf[A]$END
      """.stripMargin
  )
}

class Test15 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    InspectionBundle.message("comparing.unrelated.types.hint", "A", "B.type")

  def testObject(): Unit = checkTextHasNoErrors(
    s"""trait A
       |object B extends A
       |val a: A = _
       |${START}a == B$END
      """.stripMargin
  )

  def testObject2(): Unit = checkTextHasNoErrors(
    s"""trait A
       |object B extends A
       |class C extends A
       |val c: A = new C
       |${START}c != B$END
      """.stripMargin
  )
}

class Test16 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    InspectionBundle.message("comparing.unrelated.types.hint", "C", "B.type")

  def testObject(): Unit = checkTextHasError(
    s"""trait A
       |object B extends A
       |class C extends A
       |val c = new C
       |${START}c == B$END
      """.stripMargin
  )
}

class Test17 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    InspectionBundle.message("comparing.unrelated.types.hint", "Int", "java.lang.Integer")

  def testBoxedTypes(): Unit = checkTextHasNoErrors(
    """val i = new java.lang.Integer(0)
      |i == 100
    """.stripMargin
  )
}

class Test18 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    InspectionBundle.message("comparing.unrelated.types.hint", "Boolean", "java.lang.Boolean")

  def testBoxedTypes(): Unit = checkTextHasNoErrors(
    """val b = new java.lang.Boolean(false)
      |b equals true
    """.stripMargin
  )
}

class Test19 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    InspectionBundle.message("comparing.unrelated.types.hint", "java.lang.Integer", "Null")

  def testBoxedTypes(): Unit = checkTextHasNoErrors(
    "def test(i: Integer) = if (i == null) \"foo\" else \"bar\""
  )
}

class Test20 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    InspectionBundle.message("comparing.unrelated.types.hint", "Seq[Int]", "List[_]")

  def testExistential(): Unit = checkTextHasNoErrors(
    "Seq(1).isInstanceOf[List[_])"
  )
}

class Test21 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    InspectionBundle.message("comparing.unrelated.types.hint", "Some[Int]", "List[_]")

  def testExistential(): Unit = checkTextHasError(
    s"${START}Some(1).isInstanceOf[List[_]]$END"
  )
}

class Test22 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    InspectionBundle.message("comparing.unrelated.types.hint", "Some[_]", "Some[Int]")

  def testExistential(): Unit = checkTextHasNoErrors(
    "def foo(x: Some[_]) { x == Some(1) }"
  )
}

class Test23 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    InspectionBundle.message("comparing.unrelated.types.hint", "Some[_]", "Seq[Int]")

  def testExistential(): Unit = checkTextHasError(
    s"def foo(x: Some[_]) { ${START}x == Seq(1)$END }"
  )
}

class Test24 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    InspectionBundle.message("comparing.unrelated.types.hint", "BigInt", "Int")

  def testNumeric(): Unit = checkTextHasNoErrors(
    "BigInt(1) == 1"
  )
}

class Test25 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    InspectionBundle.message("comparing.unrelated.types.hint", "BigInt", "Long")

  def testNumeric(): Unit = checkTextHasNoErrors(
    "BigInt(1) == 1L"
  )
}

class Test26 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    InspectionBundle.message("comparing.unrelated.types.hint", "BigInt", "java.lang.Integer")

  def testNumeric(): Unit = checkTextHasNoErrors(
    "BigInt(1) == new java.lang.Integer(1)"
  )
}

class Test27 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    InspectionBundle.message("comparing.unrelated.types.hint", "BigInt", "Boolean")

  def testNumeric(): Unit = checkTextHasError(
    s"${START}BigInt(1) == true$END"
  )
}

class Test28 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    InspectionBundle.message("comparing.unrelated.types.hint", "BigInt", "String")

  def testNumeric(): Unit = checkTextHasError(
    s"${START}BigInt(1) == 1.toString$END"
  )
}

class Test29 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    InspectionBundle.message("comparing.unrelated.types.hint", "A.Coord", "Int")

  def testTypeAlias(): Unit = checkTextHasNoErrors(
    """
      |object A {
      |  type Coord = Float
      |  def isZero(n: Coord): Boolean = {
      |    n == 0
      |  }
      |}
    """.stripMargin
  )

  def testTypeAlias2(): Unit = checkTextHasError(
    s"""
       |object A {
       |  type Coord = String
       |  def isZero(n: Coord): Boolean = {
       |    ${START}n == 0$END
       |  }
       |}
      """.stripMargin
  )

  def testTypeAlias3(): Unit = checkTextHasNoErrors(
    """
      |trait A {
      |  type Coord
      |
      |  def isZero(n: Coord): Boolean = {
      |    n == 0
      |  }
      |}
    """.stripMargin
  )
}

class Test30 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    InspectionBundle.message("comparing.unrelated.types.hint", "Dummy", "Int")

  def testOverriddenMethods(): Unit = checkTextHasNoErrors(
    """
      |case class Dummy(v: Int) {
      |  def ==(value: Int): String = v + " == " + value
      |  def !=(value: Int): Boolean = v != value
      |}
      |
      |object Test {
      |  val a: String = Dummy(5) == 10
      |  val b: Boolean = Dummy(5) != 10
      |}
    """.stripMargin
  )

  def testOverriddenMethods2(): Unit = checkTextHasError(
    s"""
       |case class Dummy(v: Int) {
       |  def ==(value: Int): String = v + " == " + value
       |  def !=(value: Int): Boolean = v != value
       |}
       |
       |object Test {
       |  val b: Boolean = ${START}Dummy(5) eq 10$END
       |}
      """.stripMargin
  )

  def testOverriddenEquals(): Unit = checkTextHasError(
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
       |}
      """.stripMargin
  )

  def testOverriddenEquals2(): Unit = checkTextHasError(
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
       |}
      """.stripMargin)
}

class Test31 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    InspectionBundle.message("comparing.unrelated.types.hint", "FooBinder", "String")

  def testOverriddenWithImplicitParam(): Unit = checkTextHasError(
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
      """.stripMargin
  )
}

class Test32 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    InspectionBundle.message("comparing.unrelated.types.hint", "abc.Dummy", "cde.Dummy")

  def testSameNameTypes(): Unit = checkTextHasError(
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
      """.stripMargin
  )
}

class TestUnderscore extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    InspectionBundle.message("comparing.unrelated.types.hint", "Int", "Some[Int]")

  def testLeftIsUnderscore(): Unit = checkTextHasError(
    s"""
       |object Test {
       |  Seq(1, 2).find(${START}_ == Some(1)$END)
       |}
      """.stripMargin
  )

  def testLeftContainsUnderscoreNoErrors(): Unit = checkTextHasNoErrors(
    s"""
       |object Test {
       |  List("").filter(_.getClass.equals(classOf[String]))
       |}
      """.stripMargin
  )
}