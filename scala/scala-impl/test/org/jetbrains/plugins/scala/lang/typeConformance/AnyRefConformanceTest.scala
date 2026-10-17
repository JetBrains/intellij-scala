package org.jetbrains.plugins.scala.lang.typeConformance

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

/** `T <: AnyRef` for class types: a class conforms unless it derives from `AnyVal`; a trait conforms unless it is
 *  universal, i.e. its first parent is `Any`. A trait whose first parent is a universal trait is not universal. */
abstract class AnyRefConformanceTestBase extends TypeConformanceTestBase {

  override protected def shouldPass: Boolean = true

  private val definitions =
    """trait U extends Any
      |trait U2 extends U
      |trait W extends Any with U
      |trait T
      |trait TS extends java.io.Serializable
      |trait TU extends U with java.io.Serializable
      |trait AU extends AnyRef with U
      |class C extends U
      |object O extends U
      |class V(val x: Int) extends AnyVal
      |class VU(val x: Int) extends AnyVal with U
      |""".stripMargin

  protected def check(tpe: String, expected: Boolean): Unit =
    doTest(
      s"""$definitions
         |val x: $tpe = ???
         |val a: AnyRef = x
         |//$expected
         |""".stripMargin)

  def testUniversalTrait(): Unit = check("U", expected = false)
  def testUniversalTraitExtendingUniversalTrait(): Unit = check("W", expected = false)
  def testValueClass(): Unit = check("V", expected = false)
  def testValueClassExtendingUniversalTrait(): Unit = check("VU", expected = false)
  def testProduct(): Unit = check("Product", expected = false)
  def testEquals(): Unit = check("Equals", expected = false)
  // universal in scalac, so that value classes can extend it
  def testSerializable(): Unit = check("java.io.Serializable", expected = false)
  def testAny(): Unit = check("Any", expected = false)
  def testAnyVal(): Unit = check("AnyVal", expected = false)
  def testInt(): Unit = check("Int", expected = false)

  def testTrait(): Unit = check("T", expected = true)
  def testTraitExtendingSerializable(): Unit = check("TS", expected = true)
  def testTraitExtendingUniversalTrait(): Unit = check("U2", expected = true)
  def testUniversalTraitAndJavaInterface(): Unit = check("TU", expected = true)
  def testUniversalTraitAndAnyRef(): Unit = check("AU", expected = true)
  def testClassExtendingUniversalTrait(): Unit = check("C", expected = true)
  def testObjectExtendingUniversalTrait(): Unit = check("O.type", expected = true)
  def testString(): Unit = check("String", expected = true)
  def testNull(): Unit = check("Null", expected = true)
  def testNothing(): Unit = check("Nothing", expected = true)
  def testList(): Unit = check("List[Int]", expected = true)
  def testOption(): Unit = check("Option[Int]", expected = true)
  def testTuple(): Unit = check("(Int, String)", expected = true)
  def testFunction(): Unit = check("Int => Int", expected = true)
  def testRunnable(): Unit = check("Runnable", expected = true)
  def testCompoundWithAnyRef(): Unit = check("U with AnyRef", expected = true)
  def testCompoundWithTrait(): Unit = check("U with T", expected = true)
  def testCompoundOfUniversalTraits(): Unit = check("U with W", expected = false)

  def testTypeParameterBoundedByUniversalTrait(): Unit =
    doTest(
      s"""$definitions
         |def f[A <: U](x: A): Unit = {
         |  val a: AnyRef = x
         |}
         |//false
         |""".stripMargin)
}

class AnyRefConformanceTest_2_13 extends AnyRefConformanceTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_13
}

class AnyRefConformanceTest_3 extends AnyRefConformanceTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_3

  def testMatchable(): Unit = check("Matchable", expected = false)
  def testUnionOfUniversalTraitAndClass(): Unit = check("U | C", expected = false)
  def testUnionOfClasses(): Unit = check("C | String", expected = true)
  def testIntersectionOfUniversalTraitAndTrait(): Unit = check("U & T", expected = true)

  def testOpaqueTypeBoundedByAnyRef(): Unit =
    doTest(
      """object Ops {
        |  opaque type Op <: AnyRef = String
        |}
        |val x: Ops.Op = ???
        |val a: AnyRef = x
        |//true
        |""".stripMargin)
}
