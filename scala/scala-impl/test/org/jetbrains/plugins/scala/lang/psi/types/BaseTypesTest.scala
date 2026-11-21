package org.jetbrains.plugins.scala.lang.psi.types

import org.jetbrains.plugins.scala.{ScalaFileType, ScalaVersion}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.junit.Assert.assertEquals

/**
 * [[BaseTypes.baseType]] against scalac's `baseType`, for types with no class of their own:
 * a singleton's base type is its underlying type's, an abstract type's is its upper bound's.
 * Expected results are scalac 2.13's (found by the differential test in `lang.typePbt`).
 */
class BaseTypesTest extends ScalaLightCodeInsightFixtureTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13

  private val program =
    """object P {
      |  trait K0
      |  trait Box[+A]
      |  trait T0 { trait I1; type M2 <: I1; type F[X] <: Box[X]; val v3: I1 = ???; val v4: this.type = this; type M5 <: M2 }
      |  trait K1 {
      |    val v11: T0 = ???
      |    val v8: this.type = this
      |    type M <: K0
      |    type MM <: M
      |    type t1 = this.M
      |    type t2 = MM
      |  }
      |  val k0: K0 = ???
      |  val k00: k0.type = k0
      |  val t0: T0 = ???
      |  val k1: K1 = ???
      |  type a = k0.type
      |  type b = T0#M2
      |  type c = t0.M2
      |  type d = t0.v3.type
      |  type e = k1.v11.type
      |  type f = k1.v8.type
      |  type g = k1.M
      |  type h = T0#M5
      |  type i = t0.v4.type
      |  type j = k1.v11.M2
      |  type k = k00.type
      |  type l = k0.type with T0
      |  type m = t0.F[Int]
      |  type n = T0#M2 with K0
      |}
      |""".stripMargin

  private def check(alias: String, clazz: String, expected: String, text: String = program): Unit = {
    configureFromFileText(ScalaFileType.INSTANCE, text)
    val file = getFile.asInstanceOf[ScalaFile]
    val tpe = file.depthFirst().collectFirst { case ta: ScTypeAliasDefinition if ta.name == alias => ta }.get.aliasedType.toOption.get
    val cls = file.depthFirst().collectFirst { case c: ScTypeDefinition if c.name == clazz => c }.get
    assertEquals(s"baseType($alias, $clazz)", expected, BaseTypes.baseType(tpe, cls).map(_.canonicalText).getOrElse("<none>"))
    // the other walks over the same graph terminate too
    BaseTypes.get(tpe)
    BaseTypes.linearize(tpe).toList
  }

  def testSingletonOfVal(): Unit = check("a", "K0", "P.K0")
  def testSingletonOfValInTrait(): Unit = check("d", "I1", "P.t0.I1")
  def testSingletonOfValOfTraitType(): Unit = check("e", "T0", "P.T0")
  def testSingletonOfThisTypedVal(): Unit = check("f", "K1", "P.K1")
  def testSingletonOfThisTypedValInTrait(): Unit = check("i", "T0", "P.T0")
  def testChainOfSingletons(): Unit = check("k", "K0", "P.K0")
  def testSingletonInCompound(): Unit = check("l", "K0", "P.K0")

  def testAbstractTypeProjection(): Unit = check("b", "I1", "P.T0#I1")
  def testAbstractTypeOnPath(): Unit = check("c", "I1", "P.t0.I1")
  def testAbstractTypeOnLongerPath(): Unit = check("j", "I1", "P.k1.v11.I1")
  def testAbstractTypeOnValPath(): Unit = check("g", "K0", "P.K0")
  def testAbstractTypeBoundedByAbstractType(): Unit = check("h", "I1", "P.T0#I1")
  def testAbstractTypeBoundedByAbstractTypeInTrait(): Unit = check("t2", "K0", "P.K0")
  def testAbstractTypeOnThis(): Unit = check("t1", "K0", "P.K0")
  def testHigherKindedAbstractType(): Unit = check("m", "Box", "P.Box[Int]")
  def testAbstractTypeInCompound(): Unit = check("n", "I1", "P.T0#I1")

  // Rejected by scalac (cyclic bounds / reference), but seen while editing: the walk must stop.
  def testCyclicAbstractBounds(): Unit =
    check("q", "K0", "<none>", "object Q { trait K0; type A <: B; type B <: A; type q = A }")

  def testCyclicSingletons(): Unit =
    check("q", "K0", "<none>", "object Q { trait K0; val x: y.type = ???; val y: x.type = ???; type q = x.type }")
}
