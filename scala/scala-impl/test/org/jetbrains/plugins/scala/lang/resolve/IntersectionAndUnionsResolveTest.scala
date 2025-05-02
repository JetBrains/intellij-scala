package org.jetbrains.plugins.scala.lang.resolve
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class IntersectionAndUnionsResolveTest extends SimpleResolveTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  def testUnionPos(): Unit = checkTextHasNoErrors(
    s"""
       |object A {
       |  trait Base { def b: Int = 123 }
       |  trait Foo extends Base
       |  trait Bar extends Base
       |  val union: Foo | Bar = ???
       |  val x = union.b
       |}
       |""".stripMargin
  )

  def testUnionNeg(): Unit = checkHasErrorAroundCaret(
    s"""
       |object A {
       |  trait Base { def b: Int = 123 }
       |  trait Foo extends Base { def foo: Int = 123 }
       |  trait Bar extends Base
       |  val union: Foo | Bar = ???
       |  val foo = union.fo${CARET}o
       |}
       |""".stripMargin
  )

  def testIntersectionSimple(): Unit = checkTextHasNoErrors(
    """
      |object A {
      |  trait Resettable:
      |    def reset(): Unit
      |
      |  trait Growable[A]:
      |    def add(a: A): Unit
      |
      |  def f(x: Resettable & Growable[String]): Unit =
      |    x.reset()
      |    x.add("first")
      |}
      |""".stripMargin
  )

  def testSignatureIntersection(): Unit = checkTextHasNoErrors(
    s"""
       |object A {
       |  trait TwitterPost
       |  trait InstagramPost
       |
       |  trait Singer:
       |    def share: TwitterPost
       |
       |  trait Dancer:
       |    def share: InstagramPost
       |
       |  def share(person: Singer & Dancer): TwitterPost & InstagramPost =
       |    person.share
       |}
       |""".stripMargin
  )

  //@TODO: fix when bounds are merged
  def testSignatureIntersectionSameDesignator(): Unit = checkTextHasNoErrors(
    s"""
       |object A {
       |  trait Foo { def xs: List[Int] = ??? }
       |  trait Bar { def xs: List[String] = ??? }
       |  def x(xx: Foo & Bar): List[Int with String] = xx.xs
       |}
       |""".stripMargin
  )

  def testSignatureIntersectionSubst(): Unit = checkTextHasNoErrors(
    s"""
       |object A {
       |  trait Foo[A] { def o: Option[A] = ??? }
       |  trait Bar[B] { def o: Option[B] = ??? }
       |  def x(xx: Foo[Int] & Bar[String]): Option[Int with String] = xx.o
       |}
       |""".stripMargin
  )

  def testDeepIntersection(): Unit = checkTextHasNoErrors(
    s"""
       |object A {
       |  trait Foo[A] { def x: List[A] = ??? }
       |  trait Bar[B] { def x: List[B] = ??? }
       |  trait Baz[Z] { def x: List[Z] = ??? }
       |  trait Qux[Q] { def x: List[Q] = ??? }
       |  val x: Foo[Int] & Bar[String] & Baz[Double] & Qux[Float] = ???
       |  val xs: List[Int with String with Double with Float] = x.x
       |}
       |""".stripMargin
  )

  def testIntersectionOfUpperBoundedTypeParametersWithUnboundedTypeParameters(): Unit = {
    val code =
      """trait Foo {
        |  def foo[A, B, C <: A, D <: B](): Unit = { val x: C & D = ???; x.toString }
        |}
        |""".stripMargin

    myFixture.configureByText("Foo.scala", code)
    assertNoThrowable(() => myFixture.doHighlighting())
  }

  def testSCL21142(): Unit = checkTextHasNoErrors(
    s"""
       |trait IndividualType:
       |  type Individual
       |
       |trait FitnessType:
       |  type Fitness
       |
       |trait ExampleIndividual extends IndividualType:
       |  override type Individual = Array[Boolean]
       |
       |trait FitnessFunction:
       |  self: IndividualType & FitnessType =>
       |  def computeFitness(ind: Individual): Fitness
       |""".stripMargin
  )

  def testIntersectionWithRefinement(): Unit = checkTextHasNoErrors(
    """
      |trait A { def f: Any }
      |trait B
      |object Test {
      |  val z: B & A { def f: Int } = ???
      |  z.f
      |}
      |""".stripMargin
  )
  def testStructuralTypeInIntersection(): Unit = checkTextHasNoErrors(
    """
      |import reflect.Selectable.reflectiveSelectable
      |class Foo {
      |  def foo: String = ???
      |}
      |object Foo {
      |  private val someClass0: {val struct0: Unit} = ???
      |  private val someClass1: Foo & {val struct1: Unit} = ???
      |  private val someClass2: Foo {val struct2: Unit} = ???
      |  someClass0.struct0
      |  someClass1.struct1
      |  someClass2.struct2
      |}
      |""".stripMargin
  )
}
