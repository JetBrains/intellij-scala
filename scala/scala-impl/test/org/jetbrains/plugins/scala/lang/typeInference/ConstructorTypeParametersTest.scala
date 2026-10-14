package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

/**
 * The type parameters of a constructor are those of its class, also when the constructor is invoked inside the class,
 * where the type parameters of the class are rigid types in the arguments (see SCL-3095).
 */
class ConstructorTypeParametersTest extends TypeInferenceTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_2_13

  def testNewInsideClass(): Unit = doTest(
    s"""
       |trait Bar
       |class Foo[A <: Bar](a: A) {
       |  def copy1 = ${START}new Foo(a)$END
       |}
       |//Foo[A]
       |""".stripMargin
  )

  def testNewInsideClassWithTypeArgs(): Unit = doTest(
    s"""
       |trait Bar
       |class Foo[A <: Bar](a: A) {
       |  def copy1 = ${START}new Foo[A](a)$END
       |}
       |//Foo[A]
       |""".stripMargin
  )

  def testNewInsideClassWithExpectedType(): Unit = checkTextHasNoErrors(
    """
      |trait Bar
      |class Foo[A <: Bar](a: A) {
      |  def copy1: Foo[A] = new Foo(a)
      |  def copy2: Foo[A] = new Foo[A](a)
      |}
      |""".stripMargin
  )

  // minimized from scala.collection.mutable.AnyRefMap (Scala 2.13)
  def testOverloadedConstructorsInsideClass(): Unit = checkTextHasNoErrors(
    """
      |class AnyRefMap[K <: AnyRef](k: K) {
      |  def this(i: Int) = this(null.asInstanceOf[K])
      |
      |  def inferred: AnyRefMap[K] = new AnyRefMap(k)
      |  def explicit: AnyRefMap[K] = new AnyRefMap[K](1)
      |}
      |""".stripMargin
  )

  def testFBoundedInsideClass(): Unit = doTest(
    s"""
       |class Foo[A <: Foo[A]](a: A) {
       |  def copy1 = ${START}new Foo(a)$END
       |}
       |//Foo[A]
       |""".stripMargin
  )

  def testDependentBoundsInsideClass(): Unit = doTest(
    s"""
       |class Foo[A, B <: A](a: A, b: B) {
       |  def copy1 = ${START}new Foo(a, b)$END
       |}
       |//Foo[A, B]
       |""".stripMargin
  )

  def testLowerBoundInsideClass(): Unit = doTest(
    s"""
       |trait Bar
       |class Foo[A >: Null <: Bar](a: A) {
       |  def copy1 = ${START}new Foo(a)$END
       |}
       |//Foo[A]
       |""".stripMargin
  )

  def testOtherInstantiationInsideClass(): Unit = doTest(
    s"""
       |trait Bar
       |class Baz extends Bar
       |class Foo[A <: Bar](a: A) {
       |  def other = ${START}new Foo(new Baz)$END
       |}
       |//Foo[Baz]
       |""".stripMargin
  )

  def testNestedInstantiationInsideClass(): Unit = doTest(
    s"""
       |class Foo[A](a: A) {
       |  def nested = ${START}new Foo(this)$END
       |}
       |//Foo[Foo[A]]
       |""".stripMargin
  )

  def testCaseClassApplyInsideClass(): Unit = doTest(
    s"""
       |trait Bar
       |case class Foo[A <: Bar](a: A) {
       |  def copy1 = ${START}Foo(a)$END
       |}
       |//Foo[A]
       |""".stripMargin
  )

  def testNewOutsideClass(): Unit = doTest(
    s"""
       |trait Bar
       |class Baz extends Bar
       |class Foo[A <: Bar](a: A)
       |object Test {
       |  ${START}new Foo(new Baz)$END
       |}
       |//Foo[Baz]
       |""".stripMargin
  )

  def testNewViaAlias(): Unit = doTest(
    s"""
       |trait Bar
       |class Baz extends Bar
       |class Foo[A <: Bar, B](a: A, b: B)
       |object Test {
       |  type F[X] = Foo[Baz, X]
       |  ${START}new F(new Baz, "")$END
       |}
       |//Foo[Baz, String]
       |""".stripMargin
  )

  def testNewViaAuxiliaryConstructor(): Unit = doTest(
    s"""
       |class Foo[A](a: A) {
       |  def this(a: A, i: Int) = this(a)
       |}
       |object Test {
       |  ${START}new Foo("", 1)$END
       |}
       |//Foo[String]
       |""".stripMargin
  )

  def testSelfInvocationInAuxiliaryConstructor(): Unit = checkTextHasNoErrors(
    """
      |trait Bar
      |class Foo[A <: Bar](a: A, i: Int) {
      |  def this(a: A) = this(a, 1)
      |}
      |""".stripMargin
  )
}

/**
 * Constructor proxies (universal apply)
 */
class ConstructorTypeParametersScala3Test extends TypeInferenceTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  def testProxyInsideClass(): Unit = doTest(
    s"""
       |trait Bar
       |class Foo[A <: Bar](a: A) {
       |  def copy1 = ${START}Foo(a)$END
       |}
       |//Foo[A]
       |""".stripMargin
  )

  def testProxyViaAuxiliaryConstructor(): Unit = doTest(
    s"""
       |class Foo[A](a: A) {
       |  def this(a: A, i: Int) = this(a)
       |}
       |object Test {
       |  ${START}Foo("", 1)$END
       |}
       |//Foo[String]
       |""".stripMargin
  )
}
