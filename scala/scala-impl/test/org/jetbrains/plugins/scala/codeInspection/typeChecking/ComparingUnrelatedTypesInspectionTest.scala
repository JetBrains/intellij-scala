package org.jetbrains.plugins.scala
package codeInspection
package typeChecking

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion}
import org.junit.runner.RunWith

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_12,
  TestScalaVersion.Scala_2_13
))
abstract class ComparingUnrelatedTypesInspectionTest extends ScalaInspectionTestBase {

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[ComparingUnrelatedTypesInspection]
}

class Test1 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    ScalaInspectionBundle.message("comparing.unrelated.types.hint", "Short", "Int")

  def testWeakConformance(): Unit = checkTextHasNoErrors(
    s"""val a = 0
       |val b: Short = 1
       |${START}b == a$END
       """.stripMargin
  )
}

class Test2 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    ScalaInspectionBundle.message("comparing.unrelated.types.hint", "Double", "Int")

  def testWeakConformance(): Unit = checkTextHasNoErrors(
    s"""val a = 0
       |val b = 1.0
       |${START}b != a$END
       """.stripMargin
  )
}

class Test3 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    ScalaInspectionBundle.message("comparing.unrelated.types.hint", "Double", "Byte")

  def testWeakConformance(): Unit = checkTextHasNoErrors(
    s"""val a = 0.0
       |val b: Byte = 100
       |${START}a == b$END
       """.stripMargin
  )
}

class Test4 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    ScalaInspectionBundle.message("comparing.unrelated.types.hint", "Int", "Double")

  def testWeakConformance(): Unit = checkTextHasNoErrors(
    s"${START}1 == 1.0$END"
  )
}

class Test5 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    ScalaInspectionBundle.message("comparing.unrelated.types.hint", "Int", "Boolean")

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
    ScalaInspectionBundle.message("comparing.unrelated.types.hint", "Boolean", "Double")

  def testValueType(): Unit = checkTextHasError(
    s"""val a = true
       |val b = 0.0
       |${START}a != b$END
       """.stripMargin
  )
}

class Test7 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    ScalaInspectionBundle.message("comparing.unrelated.types.hint", "Boolean", "Int")

  def testValueType(): Unit = checkTextHasError(
    s"${START}true != 0$END"
  )
}

class Test8 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    ScalaInspectionBundle.message("comparing.unrelated.types.hint", "Array[Char]", "String")

  def testString(): Unit = checkTextHasError(
    s"""val a = "a"
       |val b = Array('a')
       |${START}b == a$END
       """.stripMargin
  )
}

class Test9 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    ScalaInspectionBundle.message("comparing.unrelated.types.hint", "String", "Int")

  def testString(): Unit = checkTextHasError(
    s"""val a = "0"
       |val b = 0
       |${START}a == b$END
       """.stripMargin
  )
}

class Test10 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    ScalaInspectionBundle.message("comparing.unrelated.types.hint", "String", "Char")

  def testString(): Unit = checkTextHasError(
    s"""val s = "s"
       |${START}s == 's'$END
       """.stripMargin
  )
}

class Test11 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    ScalaInspectionBundle.message("comparing.unrelated.types.hint", "CharSequence", "String")

  def testString(): Unit = checkTextHasNoErrors(
    s"""val a = "a"
       |val b: CharSequence = null
       |${START}b != a$END
      """.stripMargin
  )
}

class Test12 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    ScalaInspectionBundle.message("comparing.unrelated.types.hint", "scala.collection.Iterable", "scala.collection.List")

  def testInheritors(): Unit = checkTextHasNoErrors(
    s"""val a = scala.collection.Iterable(1)
       |val b = List(0)
       |${START}b == a$END
       """.stripMargin
  )
}

class Test13 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    ScalaInspectionBundle.message("comparing.unrelated.types.hint", "A", "B")

  def testInheritors(): Unit = checkTextHasNoErrors(
    s"""case class A(i: Int)
       |final class B extends A(1)
       |val a: A = A(0)
       |val b: B = new B
       |a == b
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

  def testTraits(): Unit = checkTextHasError(
    s"""trait A
       |trait B
       |val a: A = _
       |val b: B = _
       |${START}a == b$END
      """.stripMargin
  )

  def testInstanceOfTrait(): Unit = checkTextHasNoErrors(
    s"""trait A
       |trait B
       |val a: A = _
       |a.isInstanceOf[B]
      """.stripMargin
  )
}

class Test14 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    ScalaInspectionBundle.message("comparing.unrelated.types.hint", "B", "A")

  def testInheritors(): Unit = checkTextHasNoErrors(
    s"""trait A
       |object B extends A
       |${START}B.isInstanceOf[A]$END
      """.stripMargin
  )
}

class Test15 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    ScalaInspectionBundle.message("comparing.unrelated.types.hint", "A", "B.type")

  def testObject(): Unit = checkTextHasNoErrors(
    s"""trait A
       |object B extends A
       |val a: A = _
       |a == B
      """.stripMargin
  )

  def testObject2(): Unit = checkTextHasNoErrors(
    s"""trait A
       |object B extends A
       |class C extends A
       |val c: A = new C
       |c != B
      """.stripMargin
  )
}

class Test16 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    ScalaInspectionBundle.message("comparing.unrelated.types.hint", "C", "B.type")

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
    ScalaInspectionBundle.message("comparing.unrelated.types.hint", "Int", "java.lang.Integer")

  def testBoxedTypes(): Unit = checkTextHasNoErrors(
    """val i = new java.lang.Integer(0)
      |i == 100
    """.stripMargin
  )
}

class Test18 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    ScalaInspectionBundle.message("comparing.unrelated.types.hint", "Boolean", "java.lang.Boolean")

  def testBoxedTypes(): Unit = checkTextHasNoErrors(
    """val b = new java.lang.Boolean(false)
      |b equals true
    """.stripMargin
  )
}

class Test19 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    ScalaInspectionBundle.message("comparing.unrelated.types.hint", "java.lang.Integer", "Null")

  def testBoxedTypes(): Unit = checkTextHasNoErrors(
    "def test(i: Integer) = if (i == null) \"foo\" else \"bar\""
  )
}

class Test20 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    ScalaInspectionBundle.message("comparing.unrelated.types.hint", "Seq[Int]", "List[_]")

  def testExistential(): Unit = checkTextHasNoErrors(
    "Seq(1).isInstanceOf[List[_])"
  )
}

class Test21 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    ScalaInspectionBundle.message("comparing.unrelated.types.hint", "Some[Int]", "List[_]")

  def testExistential(): Unit = checkTextHasError(
    s"${START}Some(1).isInstanceOf[List[_]]$END"
  )
}

class Test22 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    ScalaInspectionBundle.message("comparing.unrelated.types.hint", "Some[_]", "Some[Int]")

  def testExistential(): Unit = checkTextHasNoErrors(
    "def foo(x: Some[_]) { x == Some(1) }"
  )
}

class Test23 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    ScalaInspectionBundle.message("comparing.unrelated.types.hint", "Some[_]", "Seq[Int]")

  def testExistential(): Unit = checkTextHasError(
    s"def foo(x: Some[_]) { ${START}x == Seq(1)$END }"
  )
}

class Test24 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    ScalaInspectionBundle.message("comparing.unrelated.types.hint", "BigInt", "Int")

  def testNumeric(): Unit = checkTextHasNoErrors(
    "BigInt(1) == 1"
  )
}

class Test25 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    ScalaInspectionBundle.message("comparing.unrelated.types.hint", "BigInt", "Long")

  def testNumeric(): Unit = checkTextHasNoErrors(
    "BigInt(1) == 1L"
  )
}

class Test26 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    ScalaInspectionBundle.message("comparing.unrelated.types.hint", "BigInt", "java.lang.Integer")

  def testNumeric(): Unit = checkTextHasNoErrors(
    "BigInt(1) == new java.lang.Integer(1)"
  )
}

class Test27 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    ScalaInspectionBundle.message("comparing.unrelated.types.hint", "BigInt", "Boolean")

  def testNumeric(): Unit = checkTextHasError(
    s"${START}BigInt(1) == true$END"
  )
}

class Test28 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    ScalaInspectionBundle.message("comparing.unrelated.types.hint", "BigInt", "String")

  def testNumeric(): Unit = checkTextHasError(
    s"${START}BigInt(1) == 1.toString$END"
  )
}

class Test29 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    ScalaInspectionBundle.message("comparing.unrelated.types.hint", "Coord", "Int")

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

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_10,
  TestScalaVersion.Scala_2_12,
  TestScalaVersion.Scala_2_13
))
class Test30 extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String = ScalaInspectionBundle.message("comparing.unrelated.types.hint", "Dummy", "Int")

  override protected def descriptionMatches(s: String): Boolean =
    s == description || s == ScalaInspectionBundle.message("comparing.unrelated.types.hint", "Dummy", "Integer")

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
    ScalaInspectionBundle.message("comparing.unrelated.types.hint", "FooBinder", "String")

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
    ScalaInspectionBundle.message("comparing.unrelated.types.hint", "abc.Dummy", "cde.Dummy")

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
    ScalaInspectionBundle.message("comparing.unrelated.types.hint", "Int", "Some[Int]")

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

// SCL-9704
class TestUnderscoreAccess extends ComparingUnrelatedTypesInspectionTest {
  override protected val description: String =
    ScalaInspectionBundle.message("comparing.unrelated.types.hint", "String", "Line")

  override def createTestText(text: String): String =
    s"""
       |case class Line(from: String, to: String)
       |
       |def findTo(path: Seq[Line], to: Line) = {
       |    $text
       |  }
       |}
       |""".stripMargin

  def testFind(): Unit = checkTextHasError(
    s"path.find(${START}_.to == to$END)"
  )

  def testFilter(): Unit = checkTextHasError(
    s"path.filter(${START}_.to == to$END)"
  )

  def testFindWithoutUnderscore(): Unit = checkTextHasError(
    s"path.find(p => ${START}p.to == to$END)"
  )

  def testFilterWithoutUnderscore(): Unit = checkTextHasError(
    s"path.find(p => ${START}p.to == to$END)"
  )
}

// SCL-13922
class TestInstanceOfAutoBoxing extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    ScalaInspectionBundle.message("comparing.unrelated.types.hint", "AnyRef", "Int")

  def testInstanceOfInt(): Unit = checkTextHasNoErrors(
    """
      |object T {
      |  def test(v: AnyRef): Unit = {
      |    println(v.isInstanceOf[Int])
      |  }
      |  def main(args: Array[String]): Unit = {
      |    val i1: Integer = new java.lang.Integer(2147483647)
      |    T.test(i1)
      |  }
      |}
      |""".stripMargin
  )
}

class TestInstanceOfAutoBoxing2 extends ComparingUnrelatedTypesInspectionTest {
  override protected val description: String =
    ScalaInspectionBundle.message("comparing.unrelated.types.hint", "Integer", "Int")

  def testInstanceOfInt(): Unit = checkTextHasNoErrors(
    """
      |def test(v: Integer): Unit = {
      |  println(v.isInstanceOf[Int])
      |}
      |""".stripMargin
  )
}

class TestInstanceOfAutoBoxing3 extends ComparingUnrelatedTypesInspectionTest {
  override protected val description: String =
    ScalaInspectionBundle.message("comparing.unrelated.types.hint", "", "Int")

  def testInstanceOfInt(): Unit = checkTextHasNoErrors(
    s"""
      |def test(v: Integer): Unit = {
      |  println(${START}v.isInstanceOf[Byte]$END)
      |}
      |""".stripMargin
  )
}

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_13
))
class TestLiteralTypes extends ComparingUnrelatedTypesInspectionTest {

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_2_13

  override protected val description: String =
    ScalaInspectionBundle.message("comparing.unrelated.types.hint", "3", "4")

  def testLiteralTypes(): Unit = checkTextHasError(
    s"""
      |val a: 3 = 3
      |val b: 4 = 4
      |println(${START}a == b$END)
      |""".stripMargin
  )

  def testLiteralTypesInInstanceOf(): Unit = checkTextHasError(
    s"""
       |val a: 3 = 3
       |println(${START}a.isInstanceOf[4]$END)
       |""".stripMargin
  )
}

// SCL-18167
class TestLambda extends ComparingUnrelatedTypesInspectionTest {

  override protected val description: String =
    ScalaInspectionBundle.message("comparing.unrelated.types.hint", "() => A", "A")

  def testLambda_with_unrelated_class(): Unit = checkTextHasError(
    s"""
       |object Test {
       |  final class A
       |  def test(f: () => A, a: A): Boolean =
       |    ${START}f == a$END
       |}
      """.stripMargin
  )

  def testLambda_with_likely_unrelated_Trait(): Unit = checkTextHasError(
    s"""
       |object Test {
       |  trait A
       |  def test(f: () => A, a: A): Boolean =
       |    ${START}f == a$END
       |}
      """.stripMargin
  )
}

class TestRange extends ComparingUnrelatedTypesInspectionTest {
  override protected val description: String =
    ScalaInspectionBundle.message("comparing.unrelated.types.hint", "Range.Inclusive", "Range.Exclusive")

  def test_comparing_inclusive_and_exclusive_range(): Unit = checkTextHasNoErrors(
    s"""
       |val r1 = 1 to 9
       |val r2 = Range(1, 10)
       |r1 == r2
       |""".stripMargin
  )
}

class TestCaseClasses extends ComparingUnrelatedTypesInspectionTest {
  override protected val description: String =
    ScalaInspectionBundle.message("comparing.unrelated.types.hint", "A", "B")

  def test_comparing_different_case_classe(): Unit = checkTextHasError(
    s"""
       |trait T
       |case class A() extends T
       |case class B() extends T
       |${START}A() == B()$END
       |""".stripMargin
  )

  def test_comparing_case_class_with_normal_class(): Unit = checkTextHasError(
    s"""
       |case class A()
       |class B { override def equals(obj: Any): Boolean = true }
       |
       |${START}A() == new B$END
       |""".stripMargin
  )

  def test_special_comparing_normal_class_with_case_class(): Unit = checkTextHasNoErrors(
    s"""
       |case class A()
       |class B { override def equals(obj: Any): Boolean = true }
       |
       |new B == A()
       |""".stripMargin
  )

  def test_normal_comparing_normal_class_with_case_class(): Unit = checkTextHasError(
    s"""
       |class A
       |case class B()
       |
       |${START}new A == B()$END
       |""".stripMargin
  )

  ///////////////// eq /////////////////


  def test_eq_case_class_with_normal_class(): Unit = checkTextHasError(
    s"""
       |case class A()
       |class B { override def equals(obj: Any): Boolean = true }
       |
       |${START}A() eq new B$END
       |""".stripMargin
  )

  def test_eq_normal_class_with_case_class(): Unit = checkTextHasError(
    s"""
       |class A { override def equals(obj: Any): Boolean = true }
       |case class B()
       |
       |${START}new A eq B()$END
       |""".stripMargin
  )
}

class TestVariousCasesWithStdTypes extends ComparingUnrelatedTypesInspectionTest {
  override protected val description = null

  override protected def descriptionMatches(s: String): Boolean =
    s != null && s.contains("Comparing unrelated types:")

  def test_any_and_any(): Unit = checkTextHasNoErrors(
    s"""
       |val a: Any = null
       |val b: Any = null
       |
       |a == b
       |""".stripMargin
  )

  def test_any_and_anyval(): Unit = checkTextHasNoErrors(
    s"""
       |val a: Any = null
       |val b: AnyVal = null
       |
       |a == b
       |""".stripMargin
  )

  def test_any_and_trait(): Unit = checkTextHasNoErrors(
    s"""
       |trait A
       |val a: Any = null
       |val b: A = null
       |
       |a == b
       |""".stripMargin
  )

  def test_any_and_class(): Unit = checkTextHasNoErrors(
    s"""
       |class A
       |val a: Any = null
       |val b: A = null
       |
       |a == b
       |""".stripMargin
  )

  def test_with_nothing(): Unit = checkTextHasNoErrors(
    s"""
       |val a: Boolean = true
       |val b: Nothing = null
       |
       |a == b
       |""".stripMargin
  )

  def test_trait_with_null(): Unit = checkTextHasNoErrors(
    s"""
       |trait A
       |val a: A = null
       |
       |a == null
       |""".stripMargin
  )

  def test_class_with_null(): Unit = checkTextHasNoErrors(
    s"""
       |class A
       |val a: A = null
       |
       |a == null
       |""".stripMargin
  )

  def test_anyref_with_null(): Unit = checkTextHasNoErrors(
    s"""
       |class A
       |val a: AnyRef = null
       |
       |a == null
       |""".stripMargin
  )

  def test_anyval_with_null(): Unit = checkTextHasError(
    s"""
       |val a: AnyVal = true
       |${START}a == null$END
       |""".stripMargin
  )

  def test_int_with_null(): Unit = checkTextHasError(
    s"""
       |val a: Int = 3
       |${START}a == null$END
       |""".stripMargin
  )

  def test_generic_type_with_null(): Unit = checkTextHasNoErrors(
    s"""
       |class Test[T](a: T) {
       |  a == null
       |}
       |""".stripMargin
  )

  def test_associated_type_with_null(): Unit = checkTextHasNoErrors(
    s"""
       |class Test {
       |  type T
       |  val a: T
       |  def b: T
       |
       |  a == null
       |  b == null
       |}
       |""".stripMargin
  )

  def test_generic_type_with_upper_bound(): Unit = checkTextHasNoErrors(
    s"""
       |class Enum[E <: Enum[E]]
       |
       |trait EnumSetProvider {
       |  type EnumSet[E <: Enum[E]] <: Int
       |  def empty[E <: Enum[E]]: EnumSet[E]
       |}
       |
       |object EnumSetProvider {
       |  val instance: EnumSetProvider =
       |    new EnumSetProvider {
       |      type EnumSet[E <: Enum[E]] = Int
       |      override def empty[E <: Enum[E]]: EnumSet[E] = 0
       |    }
       |}
       |
       |object EnumSet {
       |  import EnumSetProvider.instance
       |
       |  type EnumSet[E <: Enum[E]] = EnumSetProvider.instance.EnumSet[E]
       |
       |  def empty[E <: Enum[E]]: EnumSet[E] = instance.empty
       |
       |
       |  implicit class EnumSetOps[E <: Enum[E]](private val set: EnumSet[E]) extends AnyVal {
       |    def isEmpty: Boolean = set == EnumSet.empty
       |  }
       |}
       |""".stripMargin
  )
}
