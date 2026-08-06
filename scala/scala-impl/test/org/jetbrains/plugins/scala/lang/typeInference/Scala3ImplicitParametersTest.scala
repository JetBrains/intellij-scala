package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class Scala3ImplicitParametersTest extends ImplicitParametersTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  def testSimpleGiven(): Unit = checkNoImplicitParameterProblems(
    s"""
       |object Test {
       |  given int: Int = 123
       |  ${START}implicitly[Int]$END
       |}
       |""".stripMargin
  )

  def testAnonymousGiven(): Unit = checkNoImplicitParameterProblems(
    s"""
       |object Test {
       |  given String = "s"
       |  def foo(using String): Unit = ???
       |  ${START}foo$END
       |}
       |""".stripMargin
  )

  def testSpecExample(): Unit = checkNoImplicitParameterProblems(
    s"""
       |
       |object A {
       |  trait Ord[T]:
       |    def compare(x: T, y: T): Int
       |
       |  given intOrd: Ord[Int] with
       |    def compare(x: Int, y: Int) = ???
       |
       |  given listOrd[T](using ord: Ord[T]): Ord[List[T]] with
       |    def compare(xs: List[T], ys: List[T]): Int = ???
       |
       |  ${START}implicitly[Ord[List[Int]]]$END
       |}
       |""".stripMargin
  )

  def testAliasWithParameters(): Unit = checkNoImplicitParameterProblems(
    s"""
       |object A {
       |  trait Config
       |  trait Factory
       |  given Config = ???
       |  given (using config: Config): Factory = ???
       |  ${START}implicitly[Factory]$END
       |}
       |""".stripMargin
  )

  def testPatternBoundInstance(): Unit = checkNoImplicitParameterProblems(
    s"""
       |object A {
       |  trait Context
       |  val maybeCtx: Option[Context] = ???
       |  for given Context <- maybeContext do
       |    ${START}implicitly[Context]$END
       |}
       |""".stripMargin
  )

  def testPatternBoundInstanceNested(): Unit = checkNoImplicitParameterProblems(
    s"""
       |object A {
       |  trait Context
       |  val maybeCtx: Option[Context] = ???
       |  (maybeCtx, 1) match {
       |    case (Some(ctx @ given Context), x) => {
       |      ${START}implicitly[Context]$END
       |    }
       |  }
       |}
       |""".stripMargin
  )

  def testSCL20919(): Unit = checkNoImplicitParameterProblems(
    s"""
       |trait Result[T]:
       |  def res: T
       |
       |def run() =
       |  given Result[String] with {def res = "result"}
       |  ${START}implicitly[Result[String]]$END
       |""".stripMargin
  )

  //SCL-21109, SCL-3487
  def testEffectiveParameters_TypeParameterWithContextBoundAndContextParameter(): Unit =
    checkTextHasNoErrors(
      """def example(): Unit = {
        |  val res: String = fooContextBoundAndUsingParam[MyType]
        |}
        |
        |def fooContextBoundAndUsingParam[F : Monad](using ctx: Context): String = ???
        |
        |trait Context
        |trait Monad[F]
        |trait MyType
        |
        |given Context = ???
        |given Monad[MyType] = ???
        |""".stripMargin
    )

  //SCL-21109, SCL-3487
  def testEffectiveParameters_TypeParameterWithContextBoundAndContextParameter_1(): Unit =
    doTest(
      """object SCL3487 {
        |  class A[T](val t: T)
        |  class B[T](val t: T)
        |
        |  given a: A[String] = ???
        |  implicit val b: B[Int] = ???
        |
        |  def foo[TA: A, TB](using b: B[TB]): (TA, TB) = ???
        |
        |  /*start*/foo/*end*/
        |}
        |//(String, Int)
        |""".stripMargin
    )

  //SCL-21109, SCL-3487
  def testEffectiveParameters_TypeParameterWithContextBoundAndImplicitParameter(): Unit =
    checkTextHasNoErrors(
      """def example(): Unit = {
        |  val res: String = fooContextBoundAndImplicitParam[MyType]
        |}
        |
        |def fooContextBoundAndImplicitParam[F : Monad](implicit ctx: Context): String = ???
        |
        |trait Context
        |trait Monad[F]
        |trait MyType
        |
        |given Context = ???
        |given Monad[MyType] = ???
        |""".stripMargin
    )

  //SCL-21109, SCL-3487
  def testEffectiveParameters_TypeParameterWithContextBoundAndImplicitParameter_1(): Unit =
    doTest(
      """object SCL3487 {
        |  class A[T](val t: T)
        |  class B[T](val t: T)
        |
        |  given a: A[String] = ???
        |  implicit val b: B[Int] = ???
        |
        |  def foo[TA: A, TB](implicit b: B[TB]): (TA, TB) = ???
        |
        |  /*start*/foo/*end*/
        |}
        |//(String, Int)
        |""".stripMargin
    )

  // SCL-21319
  def testWildcardImportedGivenDefinition(): Unit = checkNoImplicitParameterProblems(
    s"""
       |trait SomeTrait:
       |  def foo: Int
       |
       |object Givens:
       |  given someTrait: SomeTrait with
       |    val foo = 2
       |
       |object Test:
       |  def foo(using someTrait: SomeTrait): Unit = println(someTrait.foo)
       |
       |  import Givens.given
       |  ${START}foo$END
       |""".stripMargin
  )

  def testTopLevelGivenDefinition(): Unit = {
    myFixture.addFileToProject(
      "topLevelGiven.scala",
      """
        |package foo
        |
        |trait F[A] { def foo: A = ??? }
        |given fInt: F[Int] with
        |  def foo: Int = 12
        |""".stripMargin
    )

    checkNoImplicitParameterProblems(
      s"""
        |package foo
        |object A {
        |  ${START}implicitly[F[Int]]$END
        |}
        |""".stripMargin
    )
  }

  def testNestingSimple(): Unit =
    checkNoImplicitParameterProblems(
      s"""
        |object A {
        |  def foo(using ev: Int) = {
        |    def bar(using ev2: Int) = {
        |      ${START}summon[Int]$END
        |    }
        |  }
        |}
        |""".stripMargin
    )

  def testSCL23504(): Unit = checkNoImplicitParameterProblems(
    s"""
       |trait Encode[A]:
       |    def encode(a: A): String
       |
       |trait Channel[A]:
       |  def write[A](obj: A)(using enc: Encode[A]): Unit
       |
       |object FinalChannel extends Channel:
       |  override def write[A](obj: A)(using enc: Encode[A]): Unit =
       |    println(enc.encode(obj))
       |
       |class StringEncoder extends Encode[String]:
       |  override def encode(s: String) = s
       |
       |given StringEncoder
       |
       |@main
       |def main: Unit =
       |  given newStringEncoder: Encode[String] =
       |    (s: String) => s + "!"
       |
       |  ${START}FinalChannel.write("hello")$END
       |
       |""".stripMargin
  )

  //SCL-24883
  def testDerives_InLocalClass(): Unit = checkTextHasNoErrors(
    s"""class Test {
       |  trait CaseClassName[A]:
       |    def get: String
       |
       |  object CaseClassName:
       |    inline final def derived[A](using inline A: scala.deriving.Mirror.Of[A]): CaseClassName[A] = new CaseClassName[A]:
       |      def get = A.toString
       |
       |  case class CoolClass(i: Int) derives CaseClassName
       |
       |  def print(): Unit =
       |    println(summon[CaseClassName[CoolClass]].get)
       |}
       |""".stripMargin
  )

  def testDerives_InLocalClass_InInnerObjects(): Unit = checkTextHasNoErrors(
    s"""class Test {
       |  object Inner {
       |    trait CaseClassName[A]:
       |      def get: String
       |
       |    object CaseClassName:
       |      inline final def derived[A](using inline A: scala.deriving.Mirror.Of[A]): CaseClassName[A] = new CaseClassName[A]:
       |        def get = A.toString
       |  }
       |
       |  object Inner2 {
       |    import Inner.CaseClassName
       |    case class CoolClass(i: Int) derives CaseClassName
       |  }
       |
       |  import Inner.*
       |  import Inner2.*
       |
       |  def print(): Unit =
       |    println(summon[CaseClassName[CoolClass]].get)
       |}
       |""".stripMargin
  )

  def testDerives_InLocalClass_InInnerObjects_WithAliasedTypeClassName(): Unit = checkTextHasNoErrors(
    s"""class Test {
       |  object Inner {
       |    trait CaseClassName[A]:
       |      def get: String
       |
       |    object CaseClassName:
       |      inline final def derived[A](using inline A: scala.deriving.Mirror.Of[A]): CaseClassName[A] = new CaseClassName[A]:
       |        def get = A.toString
       |  }
       |
       |  object Inner2 {
       |    import Inner.{CaseClassName as CaseClassAliasName}
       |    case class CoolClass(i: Int) derives CaseClassAliasName
       |  }
       |
       |  import Inner.*
       |  import Inner2.*
       |
       |  def print(): Unit =
       |    println(summon[CaseClassName[CoolClass]].get)
       |}
       |""".stripMargin
  )
}
