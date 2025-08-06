package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion, TypecheckerTests}
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class Scala3OpaqueTypeAliasTest extends ScalaLightCodeInsightFixtureTestCase {
  override protected def supportedIn(version: ScalaVersion) = version >= LatestScalaVersions.Scala_3_0

  def testLhsLhsInside(): Unit = {
    checkTextHasNoErrors(
      """
        |object Inside:
        |  opaque type T = Int
        |  val v: T = ??? : T
      """.stripMargin
    )
  }

  def testLhsRhsInside(): Unit = {
    checkTextHasNoErrors(
      """
        |object Inside:
        |  opaque type T = Int
        |  val v: T = ??? : Int
      """.stripMargin
    )
  }

  def testRhsLhsInside(): Unit = {
    checkTextHasNoErrors(
      """
        |object Inside:
        |  opaque type T = Int
        |  val v: Int = ??? : T
      """.stripMargin
    )
  }

  def testLhsLhsOutside(): Unit = {
    checkTextHasNoErrors(
      """
        |object Inside:
        |  opaque type T = Int
        |object Outside:
        |  val v: Inside.T = ??? : Inside.T
      """.stripMargin
    )
  }

  def testLhsRhsOutside(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |object Inside:
         |  opaque type T = Int
         |object Outside:
         |  val v: Inside.T = ??? : ${CARET}Int
      """.stripMargin
    )
  }

  def testRhsLhsOutside(): Unit = {
    checkHasErrorAroundCaret(
      s"""
        |object Inside:
        |  opaque type T = Int
        |object Outside:
        |  val v: Int = ??? : ${CARET}Inside.T
      """.stripMargin
    )
  }

  def testNestedScope(): Unit = {
    checkTextHasNoErrors(
      s"""
         |class Inside:
         |  opaque type T = Int
         |  class Nested:
         |    val v: Int = ??? : T
         |""".stripMargin
    )
  }

  def testCompanionObject(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |class Inside:
         |  opaque type T = Int
         |object Inside:
         |  val v: Int = ??? : ${CARET}Inside#T
         |""".stripMargin
    )
  }

  def testCompanionClass(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |class Inside:
         |  val v: Int = ??? : ${CARET}Inside.T
         |object Inside:
         |  opaque type T = Int
         |""".stripMargin
    )
  }

  def testSuperclass(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |trait Foo:
         |  opaque type T = Int
         |trait Bar extends Foo:
         |  val x: T = ${CARET}1
         |""".stripMargin
    )
  }

  def testTransitive1(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Inside:
         |  opaque type T = Int
         |  val v: Int = ??? : Outside.T
         |object Outside:
         |  type T = Inside.T
      """.stripMargin
    )
  }

  def testTransitive2(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |object Inside:
         |  opaque type T = Int
         |object Outside:
         |  type T = Inside.T
         |  val v: Int = ??? : ${CARET}T
      """.stripMargin
    )
  }

  def testComponent1(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Inside:
         |  opaque type T = Int
         |  val v: Option[Int] = ??? : Option[T]
      """.stripMargin
    )
  }

  def testComponent2(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |object Inside:
         |  opaque type T = Int
         |object Outside:
         |  val v: Option[Int] = ??? : ${CARET}Option[Inside.T]
      """.stripMargin
    )
  }

  def testComposite1(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Inside:
         |  opaque type TC[A] = Option[A]
         |  val v: Option[Int] = ??? : TC[Int]
      """.stripMargin
    )
  }

  def testComposite2(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |object Inside:
         |  opaque type TC[A] = Option[A]
         |object Outside:
         |  val v: Option[Int] = ??? : ${CARET}Inside.TC[Int]
      """.stripMargin
    )
  }

  def testCompositeAndComponent1(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Inside:
         |  opaque type T = Int
         |  opaque type TC[A] = Option[A]
         |  val v: Option[Int] = ??? : TC[T]
      """.stripMargin
    )
  }

  def testCompositeAndComponent2(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |object Inside:
         |  opaque type T = Int
         |  opaque type TC[A] = Option[A]
         |object Outside:
         |  val v: Option[Int] = ??? : ${CARET}Inside.TC[Inside.T]
      """.stripMargin
    )
  }

  def testSuperType1(): Unit = {
    checkTextHasNoErrors(
      s"""
         |class TC[A]
         |object Inside:
         |  opaque type T = Int
         |  class Foo extends TC[T]
         |  val v: TC[Int] = ??? : Foo
      """.stripMargin
    )
  }

  def testSuperType2(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |class TC[A]
         |object Inside:
         |  opaque type T = Int
         |  class Foo extends TC[T]
         |object Outside:
         |  val v: TC[Int] = ??? : ${CARET}Inside.Foo
      """.stripMargin
    )
  }

  def testExpression(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |object Inside:
         |  opaque type T = Int
         |object Outside:
         |  val v: Inside.T = ${CARET}123
      """.stripMargin
    )
  }

  def testPattern(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |object Inside:
         |  opaque type T = Int
         |object Outside:
         |  val Some(x) = ??? : Some[Inside.T]
         |  val y: Int = ${CARET}x
      """.stripMargin
    )
  }

  def testMethod(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |class Foo:
         |  def foo(x: Int): Unit = ???
         |object Inside:
         |  opaque type T = Foo
         |object Outside:
         |  val v: Inside.T = ???
         |  v.${CARET}foo(1)
      """.stripMargin
    )
  }

  def testParameterlessMethod(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |class Foo:
         |  def foo: Int = ???
         |object Inside:
         |  opaque type T = Foo
         |object Outside:
         |  val v: Inside.T = ???
         |  v.${CARET}foo
      """.stripMargin
    )
  }

  def testVal(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |class Foo:
         |  val foo: Int = ???
         |object Inside:
         |  opaque type T = Foo
         |object Outside:
         |  val v: Inside.T = ???
         |  v.${CARET}foo
      """.stripMargin
    )
  }

  def testVar(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |class Foo:
         |  var foo: Int = ???
         |object Inside:
         |  opaque type T = Foo
         |object Outside:
         |  val v: Inside.T = ???
         |  v.${CARET}foo = 1
      """.stripMargin
    )
  }

  def testClass(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |class Foo:
         |  class Bar
         |object Inside:
         |  opaque type T = Foo
         |object Outside:
         |  val v: Inside.T#${CARET}Bar = ???
      """.stripMargin
    )
  }

  def testTypeAlias(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |class Foo:
         |  type Bar = Int
         |object Inside:
         |  opaque type T = Foo
         |object Outside:
         |  val v: Inside.T#${CARET}Bar = ???
      """.stripMargin
    )
  }

  def testAbstractTypeMember(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |trait Foo:
         |  type Bar
         |object Inside:
         |  opaque type T = Foo
         |object Outside:
         |  val v: Inside.T#${CARET}Bar = ???
      """.stripMargin
    )
  }

  def testValParameter(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |class Foo(val foo: Int)
         |object Inside:
         |  opaque type T = Foo
         |object Outside:
         |  val v: Inside.T = ???
         |  v.${CARET}foo
      """.stripMargin
    )
  }

  def testVarParameter(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |class Foo(var foo: Int)
         |object Inside:
         |  opaque type T = Foo
         |object Outside:
         |  val v: Inside.T = ???
         |  v.${CARET}foo = 1
      """.stripMargin
    )
  }

  def testTypeParameter(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |class Foo[Bar]
         |object Inside:
         |  opaque type T = Foo[Int]
         |object Outside:
         |  val v: Inside.T[${CARET}Int] = ???
      """.stripMargin
    )
  }

  def testPrimaryConstructor(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |class Foo(x: Int)
         |object Inside:
         |  opaque type T = Foo
         |object Outside:
         |  new ${CARET}Inside.T(1)
      """.stripMargin
    )
  }

  def testAuxiliaryConstructor(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |class Foo:
         |  def this(x: Int) = this()
         |object Inside:
         |  opaque type T = Foo
         |object Outside:
         |  new ${CARET}Inside.T(1)
      """.stripMargin
    )
  }

//  def testUniversalApplyMethod(): Unit = {
//    checkHasErrorAroundCaret(
//      s"""
//         |class Foo(x: Int)
//         |object Inside:
//         |  opaque type T = Foo
//         |object Outside:
//         |  ${CARET}Inside.T(1)
//      """.stripMargin
//    )
//  }

  def testMethodApplicability(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |object Inside:
         |  opaque type T = Int
         |  def foo(x: T): Unit = ???
         |object Outside:
         |  Inside.foo(${CARET}123)
      """.stripMargin
    )
  }

  def testConstructorApplicability(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |object Inside:
         |  opaque type T = Int
         |  class Foo(x: T)
         |object Outside:
         |  new Inside.Foo(${CARET}123)
      """.stripMargin
    )
  }

  // TODO Union type, SCL-23806
  def testLeastUpperBoundIf(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |object Inside:
         |  opaque type T = Int
         |object Outside:
         |  val x = if (???) 123 else ??? : Inside.T
         |  val y: Inside.T = ${CARET}x
         |""".stripMargin
    )
  }

  // Union type, SCL-23807
  def testLeastUpperBoundTypeArgument(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |object Inside:
         |  opaque type T = Int
         |object Outside:
         |  def f[A](x: A, y: A): A = ???
         |  val x = f(123, ??? : Inside.T)
         |  val y: Inside.T = ${CARET}x
         |""".stripMargin
    )
  }

  def testLowerBound0(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |object Inside:
         |  opaque type T >: AnyVal = ${CARET}Int
         |""".stripMargin
    )
  }

  def testLowerBound1(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Inside:
         |  opaque type T >: Int = Any
         |object Outside:
         |  val v: Inside.T = ??? : Int
         |""".stripMargin
    )
  }

  def testLowerBound2(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |object Inside:
         |  opaque type T >: Int = Any
         |object Outside:
         |  val v: Int = ??? : ${CARET}Inside.T
         |""".stripMargin
    )
  }

  def testLowerBound3(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Inside:
         |  opaque type T >: Int = Any
         |object Outside:
         |  val v: Inside.T = ??? : Inside.T
         |""".stripMargin
    )
  }

  def testLowerBound4(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Inside:
         |  opaque type T >: Int = AnyVal
         |  val v: T = ??? : Long
         |""".stripMargin
    )
  }

  def testLowerBound5(): Unit = {
    checkTextHasNoErrors(
      s"""
         |opaque type TC[A] >: A = A
         |val v: TC[Int] = ??? : Int
         |""".stripMargin
    )
  }

  def testLowerBound6(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Inside:
         |  opaque type TC[A] >: A = A
         |object Outside:
         |  val v: Inside.TC[Int] = ??? : Int
         |""".stripMargin
    )
  }

  def testUpperBound0(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |object Inside:
         |  opaque type T <: Int = ${CARET}AnyVal
         |""".stripMargin
    )
  }

  def testUpperBound1(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Inside:
         |  opaque type T <: Int = Nothing
         |object Outside:
         |  val v: Int = ??? : Inside.T
         |""".stripMargin
    )
  }

  def testUpperBound2(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |object Inside:
         |  opaque type T <: Int = Nothing
         |object Outside:
         |  val v: Inside.T = ??? : ${CARET}Int
         |""".stripMargin
    )
  }

  def testUpperBound3(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Inside:
         |  opaque type T <: Int = Nothing
         |object Outside:
         |  val v: Inside.T = ??? : Inside.T
         |""".stripMargin
    )
  }

  def testUpperBound4(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Inside:
         |  opaque type T <: AnyVal = Int
         |  val v: Int = ??? : T
         |""".stripMargin
    )
  }

  def testUpperBound5(): Unit = {
    checkTextHasNoErrors(
      s"""
         |opaque type TC[A] <: A = A
         |val v: Int = ??? : TC[Int]
         |""".stripMargin
    )
  }

  def testUpperBound6(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Inside:
         |  opaque type TC[A] <: A = A
         |object Outside:
         |  val v: Int = ??? : Inside.TC[Int]
         |""".stripMargin
    )
  }

  def testLowerAndUpperBounds(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |object Inside:
         |  opaque type T >: Any <: Nothing = ${CARET}Int
         |""".stripMargin
    )
  }

  def testImplicitArgumentLhsLhsInside(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Inside:
         |  opaque type T = Int
         |  def find(implicit x: T): Unit = ()
         |  implicit val y: T = ???
         |  find
         |""".stripMargin
    )
  }

  def testImplicitArgumentLhsRhsInside(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Inside:
         |  opaque type T = Int
         |  def find(implicit x: T): Unit = ()
         |  implicit val y: Int = ???
         |  find
         |""".stripMargin
    )
  }

  def testImplicitArgumentRhsLhsInside(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Inside:
         |  opaque type T = Int
         |  def find(implicit x: Int): Unit = ()
         |  implicit val y: T = ???
         |  find
         |""".stripMargin
    )
  }

  def testImplicitArgumentLhsLhsOutside(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Inside:
         |  opaque type T = Int
         |  def find(implicit x: T): Unit = ()
         |object Outside:
         |  implicit val y: Inside.T = ???
         |  Inside.find
         |""".stripMargin
    )
  }

  def testImplicitArgumentLhsRhsOutside(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |object Inside:
         |  opaque type T = Int
         |  def find(implicit x: T): Unit = ()
         |object Outside:
         |  implicit val y: Int = ???
         |  ${CARET}Inside.find
         |""".stripMargin
    )
  }

  def testImplicitArgumentRhsLhsOutside(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |object Inside:
         |  opaque type T = Int
         |  def find(implicit x: Int): Unit = ()
         |object Outside:
         |  implicit val y: Inside.T = ???
         |  ${CARET}Inside.find
         |""".stripMargin
    )
  }

  def testImplicitArgumentUpperBound(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Inside:
         |  opaque type T <: Int = Nothing
         |  def find(implicit x: Int): Unit = ()
         |object Outside:
         |  implicit val y: Inside.T = ???
         |  Inside.find
         |""".stripMargin
    )
  }

  def testImplicitConversionLhsLhsInside(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Inside:
         |  opaque type T = Int
         |  implicit def from(x: T): Boolean = x > 0
         |  val b: Boolean = ??? : T
      """.stripMargin
    )
  }

  def testImplicitConversionLhsRhsInside(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Inside:
         |  opaque type T = Int
         |  implicit def from(x: T): Boolean = x > 0
         |  val b: Boolean = ??? : Int
      """.stripMargin
    )
  }

  def testImplicitConversionRhsLhsInside(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Inside:
         |  opaque type T = Int
         |  implicit def from(x: Int): Boolean = x > 0
         |  val b: Boolean = ??? : T
      """.stripMargin
    )
  }

  def testImplicitConversionLhsLhsOutside(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Inside:
         |  opaque type T = Int
         |  implicit def from(x: T): Boolean = x > 0
         |object Outside:
         |  import Inside.*
         |  val b: Boolean = ??? : T
      """.stripMargin
    )
  }

  def testImplicitConversionLhsRhsOutside(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |object Inside:
         |  opaque type T = Int
         |  implicit def from(x: T): Boolean = x > 0
         |object Outside:
         |  import Inside.*
         |  val b: Boolean = ??? : ${CARET}Int
      """.stripMargin
    )
  }

  def testImplicitConversionRhsLhsOutside(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |object Inside:
         |  opaque type T = Int
         |  implicit def from(x: Int): Boolean = x > 0
         |object Outside:
         |  import Inside.*
         |  val b: Boolean = ??? : ${CARET}Inside.T
         |""".stripMargin
    )
  }

  def testImplicitConversionUpperBound(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Inside:
         |  opaque type T <: Int = Nothing
         |  implicit def from(x: Int): Boolean = x > 0
         |object Outside:
         |  import Inside.*
         |  val b: Boolean = ??? : Inside.T
         |""".stripMargin
    )
  }

  def testExtensionLhsLhsInside(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Inside:
         |  opaque type T = Int
         |  extension (that: T)
         |    def method(): Unit = ???
         |  (??? : T).method()
      """.stripMargin
    )
  }

  def testExtensionLhsRhsInside(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Inside:
         |  opaque type T = Int
         |  extension (that: T)
         |    def method(): Unit = ???
         |  (??? : Int).method()
      """.stripMargin
    )
  }

  def testExtensionRhsLhsInside(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Inside:
         |  opaque type T = Int
         |  extension (that: Int)
         |    def method(): Unit = ???
         |  (??? : T).method()
      """.stripMargin
    )
  }

  def testExtensionLhsLhsOutside(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Inside:
         |  opaque type T = Int
         |  extension (that: T)
         |    def method(): Unit = ???
         |object Outside:
         |  import Inside.*
         |  (??? : T).method()
      """.stripMargin
    )
  }

  def testExtensionLhsRhsOutside(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |object Inside:
         |  opaque type T = Int
         |  extension (that: T)
         |    def method(): Unit = ???
         |object Outside:
         |  import Inside.*
         |  (??? : Int).${CARET}method()
      """.stripMargin
    )
  }

  def testExtensionRhsLhsOutside(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |object Inside:
         |  opaque type T = Int
         |  extension (that: Int)
         |    def method(): Unit = ()
         |object Outside:
         |  import Inside.*
         |  (??? : T).${CARET}method()
      """.stripMargin
    )
  }

  def testExtensionUpperBound(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Inside:
         |  opaque type T <: Int = Nothing
         |  extension (that: Int)
         |    def method(): Unit = ()
         |object Outside:
         |  import Inside.*
         |  (??? : T).method()
      """.stripMargin
    )
  }

  def testImplicitInCompanionObjectRhsLhsInside(): Unit = {
    checkTextHasNoErrors(
      s"""
         |class Foo
         |object Foo:
         |  implicit val x: Foo = ???
         |object Inside:
         |  opaque type T = Foo
         |  implicitly[T]
         |""".stripMargin
    )
  }

  def testImplicitInCompanionObjectLhsLhsInside(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |object Inside:
         |  opaque type T = Int
         |  object T:
         |    implicit val x: T = ???
         |  ${CARET}implicitly[T]
         |""".stripMargin
    )
  }

  def testImplicitInCompanionObjectLhsOutside(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |class Foo
         |object Foo:
         |  implicit val x: Foo = ???
         |object Inside:
         |  opaque type T = Foo
         |object Outside:
         |  ${CARET}implicitly[Inside.T]
         |""".stripMargin
    )
  }

  def testImplicitInCompanionObjectLhsLhsOutside(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Inside:
         |  opaque type T = Int
         |  object T:
         |    implicit val x: T = ???
         |object Outside:
         |  implicitly[Inside.T]
         |""".stripMargin
    )
  }

  def testImplicitInCompanionObjectUpperBound(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |class Foo
         |object Foo:
         |  implicit val x: Foo = ???
         |object Inside:
         |  opaque type T <: Foo = Nothing
         |object Outside:
         |  ${CARET}implicitly[Inside.T]
         |""".stripMargin
    )
  }

  def testContextFunctionInside(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Inside:
         |  opaque type TC[A] = Boolean
         |  def method(x: TC[Int] ?=> Unit): Unit = ???
         |  method { implicitly[Inside.TC[Int]] }
         |""".stripMargin
    )
  }

  def testContextFunctionOutside(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Inside:
         |  opaque type TC[A] = Boolean
         |  def method(x: TC[Int] ?=> Unit): Unit = ???
         |object Outside:
         |  Inside.method { implicitly[Inside.TC[Int]] }
         |""".stripMargin
    )
  }

  def testErasure(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |object Inside:
         |  opaque type T = Int
         |object Outside:
         |  def foo(x: Int): Unit = ???
         |  def ${CARET}foo(x: Inside.T): Unit = ???
         |""".stripMargin
    )
  }

  def testErasureIArray0(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |object Outside:
         |  def foo(x: IArray[Int]): Unit = ???
         |  def ${CARET}foo(x: IArray[Int]): Unit = ???
         |""".stripMargin
    )
  }

  //  def testErasureIArray1(): Unit = {
//    checkHasErrorAroundCaret(
//      s"""
//         |object Outside:
//         |  def foo(x: Array[_ <: Int]): Unit = ???
//         |  def ${CARET}foo(x: IArray[Int]): Unit = ???
//         |""".stripMargin
//    )
//  }

  def testErasureIArray2(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Outside:
         |  def foo(x: Array[_ <: Int]): Unit = ???
         |  def foo(x: IArray[String]): Unit = ???
         |""".stripMargin
    )
  }

  def testErasureIArray3(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Outside:
         |  def foo(x: AnyRef): Unit = ???
         |  def foo(x: IArray[Int]): Unit = ???
         |""".stripMargin
    )
  }

  def testErasureIArray4(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Outside:
         |  def foo(x: IArray[Int]): Unit = ???
         |  def foo(x: IArray[Long]): Unit = ???
         |""".stripMargin
    )
  }

  def testErasureIArray5(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |object Outside:
         |  def foo(x: IArray[Option[Int]]): Unit = ???
         |  def ${CARET}foo(x: IArray[Option[Long]]): Unit = ???
         |""".stripMargin
    )
  }

  def testCachingEquivalence1(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |class Foo
         |object Inside:
         |  opaque type T = Foo
         |  val x: Foo = ??? : Inside.T
         |object Outside:
         |  val y: Foo = ??? : ${CARET}Inside.T
         |""".stripMargin
    )
  }

  def testCachingEquivalence2(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |class Foo
         |object Outside:
         |  val y: Foo = ??? : ${CARET}Inside.T
         |object Inside:
         |  opaque type T = Foo
         |  val x: Foo = ??? : Inside.T
         |""".stripMargin
    )
  }

  def testCachingConformance1(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |class Foo; class Bar extends Foo
         |object Inside:
         |  opaque type T = Bar
         |  val x: Foo = ??? : Inside.T
         |object Outside:
         |  val y: Foo = ??? : ${CARET}Inside.T
         |""".stripMargin
    )
  }

  def testCachingConformance2(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |class Foo; class Bar extends Foo
         |object Outside:
         |  val y: Foo = ??? : ${CARET}Inside.T
         |object Inside:
         |  opaque type T = Bar
         |  val x: Foo = ??? : Inside.T
         |""".stripMargin
    )
  }

  def testCachingImplicits(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |object Inside:
         |  opaque type T = Int
         |object Outside:
         |  val x: Int = implicitly[Inside.T]
         |  val y: Int = ??? : ${CARET}Inside.T
         |""".stripMargin
    )
  }

  def testTreeContext(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Inside:
         |  opaque type T[+A] = Seq[A]
         |  for (x <- ??? : T[Int]; if x > 0) yield x + 1
         |""".stripMargin
    )
  }

  def testScl21568(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Inside:
         |  opaque type T = Nothing
         |  object T:
         |    extension (x: T)
         |      def foo: Int = 123
         |object Outside:
         |  (??? : Inside.T).foo
         |""".stripMargin
    )
  }

  def testScl22480(): Unit = {
    checkTextHasNoErrors(
      s"""
         |trait Base
         |case class Child(x: String) extends Base
         |
         |opaque type Id[+T <: Base] = Int
         |
         |def update[T <: Base](id: Id[T])(update: (Double, T) => T) = ()
         |
         |def foo(): Unit = {
         |  val id: Id[Child] = ???
         |  update(id)((x,child) => child.copy(x = ""))
         |}
         |""".stripMargin
    )
  }

  def testLiteralTypeWidening(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |object Inside:
         |  opaque type T = 123
         |object Outside:
         |  var x = ??? : Inside.T
         |  x = ${CARET}123
         |""".stripMargin
    )
  }

  def testConstraintSolving(): Unit = {
    checkTextHasNoErrors(
      s"""
         |class Foo
         |object Inside:
         |  opaque type T[_] = Int
         |  def foo[A](x: T[A]): A = ???
         |  var v = foo(??? : T[Foo])
         |  v = new Foo()
         |""".stripMargin
    )
  }
}
