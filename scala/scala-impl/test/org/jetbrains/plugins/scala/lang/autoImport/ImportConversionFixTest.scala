package org.jetbrains.plugins.scala.lang.autoImport

import org.jetbrains.plugins.scala.autoImport.quickFix.ImportImplicitConversionFixes
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class ImportConversionFixTest extends ImportElementFixTestBase[ScReferenceExpression] {
  //conversions from standard library may be different in older versions
  override protected def supportedIn(version: ScalaVersion) = version >= LatestScalaVersions.Scala_2_13

  override def createFix(ref: ScReferenceExpression) = ImportImplicitConversionFixes(ref).find(_.elements.nonEmpty)

  def testAsJavaCollection(): Unit = checkElementsToImport(
    s"""object Test {
       |  Seq("").${CARET}asJavaCollection
       |}
       |""".stripMargin,
    "scala.jdk.CollectionConverters.IterableHasAsJava",
    "scala.collection.JavaConverters.asJavaCollectionConverter",
  )

  def testAsJavaCollectionOldExcluded(): Unit =
    withExcluded("scala.collection.JavaConverters") {
      checkElementsToImport(
        s"""object Test {
           |  Seq("").${CARET}asJavaCollection
           |}
           |""".stripMargin,
        "scala.jdk.CollectionConverters.IterableHasAsJava"
      )
    }


  def testAsJava(): Unit = checkElementsToImport(
    s"""object Test {
       |  Seq("").${CARET}asJava
       |}
       |""".stripMargin,

    "scala.jdk.CollectionConverters.IterableHasAsJava",
    "scala.jdk.CollectionConverters.SeqHasAsJava",
    "scala.jdk.FunctionConverters.enrichAsJavaFunction",
    "scala.jdk.FunctionConverters.enrichAsJavaIntFunction",
    "scala.collection.JavaConverters.asJavaIterableConverter",
    "scala.collection.JavaConverters.seqAsJavaListConverter"
  )

  def testAsScala(): Unit = checkElementsToImport(
    s"""class Test {
       |  val list: java.util.ArrayList[String] = ???
       |  list.${CARET}asScala
       |}
       |""".stripMargin,
    "scala.jdk.CollectionConverters.CollectionHasAsScala",
    "scala.jdk.CollectionConverters.IterableHasAsScala",
    "scala.jdk.CollectionConverters.ListHasAsScala",
    "scala.collection.JavaConverters.asScalaBufferConverter",
    "scala.collection.JavaConverters.collectionAsScalaIterableConverter",
    "scala.collection.JavaConverters.iterableAsScalaIterableConverter",
  )

  def testSecondsPostfix(): Unit = checkElementsToImport(
    s"""class Test {
       |  100 ${CARET}seconds
       |}
       |""".stripMargin,
    "scala.concurrent.duration.DurationInt",
  )

  def testDoubleSecondsInfix(): Unit = checkElementsToImport(
    s"""class Test {
       |  1.5 ${CARET}seconds fromNow
       |}
       |""".stripMargin,
    "scala.concurrent.duration.DurationDouble",
  )

  def testGenericImplicitClass(): Unit = doTest(
    fileText =
      s"""
         |object conversions {
         |  implicit class ObjectExt[T](private val v: T) extends AnyVal {
         |    def toOption: Option[T] = Option(v)
         |  }
         |}
         |class Test {
         |  "".${CARET}toOption
         |}
         |""".stripMargin,
    expectedText =
      """
        |import conversions.ObjectExt
        |
        |object conversions {
        |  implicit class ObjectExt[T](private val v: T) extends AnyVal {
        |    def toOption: Option[T] = Option(v)
        |  }
        |}
        |class Test {
        |  "".toOption
        |}""".stripMargin,

    "conversions.ObjectExt"
  )

  def testGenericImplicitClassWithBound(): Unit = checkElementsToImport(
    s"""trait Element
       |object MyElement extends Element
       |
       |object conversions {
       |  implicit class ElementExt[E <: Element](private val element: E) extends AnyVal {
       |    def doSomething(): Unit = ???
       |  }
       |}
       |
       |class Test {
       |  MyElement.${CARET}doSomething()
       |}
       |""".stripMargin,

    "conversions.ElementExt"
  )

  def testStringInterpolation(): Unit = checkElementsToImport(
    s"""
       |object StringInterpol {
       |  implicit class Xy(val sc: StringContext) extends AnyVal {
       |    def xy(args: Any*): BigDecimal = BigDecimal(sc.parts.head)
       |  }
       |}
       |
       |class Test {
       |  ${CARET}xy"100500"
       |}
       |""".stripMargin,

    "StringInterpol.Xy"
  )

  def testParamWithTypeConstructor(): Unit = checkElementsToImport(
    s"""
       |object conversions {
       |  implicit class SeqExt[CC[X] <: Seq[X], A <: AnyRef](private val value: CC[A]) extends AnyVal {
       |    def firstBy[B](f: A => B)(implicit ord: Ordering[B]): Option[A] = ???
       |  }
       |}
       |
       |class Test {
       |  Nil.${CARET}firstBy()
       |}
       |""".stripMargin,

    "conversions.SeqExt"
  )

  def testImplicitParameterOfImplicitConversion1(): Unit = checkElementsToImport(
    s"""
      |object show {
      |  trait Show[T] {
      |    def show(t: T): String
      |  }
      |  trait Ops {
      |    def show: String = ???
      |  }
      |}
      |
      |object implicits {
      |  implicit def toShow[A](target: A)(implicit tc: show.Show[A]): show.Ops = ???
      |  implicit val stringShow: show.Show[String] = ???
      |}
      |
      |class Test {
      |  "ab".${CARET}show
      |}""".stripMargin,
    "implicits.toShow"
  )

  def testImplicitParameterOfImplicitConversion2(): Unit = checkElementsToImport(
    s"""
       |import implicits.toShow
       |
       |object show {
       |  trait Show[T] {
       |    def show(t: T): String
       |  }
       |  trait Ops {
       |    def show: String = ???
       |  }
       |}
       |
       |object implicits {
       |  implicit def toShow[A](target: A)(implicit tc: show.Show[A]): show.Ops = ???
       |  implicit val stringShow: show.Show[String] = ???
       |}
       |
       |class Test {
       |  "ab".${CARET}show
       |}""".stripMargin,

    "implicits.stringShow"
  )

  def testImportFromVal(): Unit = checkElementsToImport(
    s"""
      |trait Owner {
      |  trait A
      |  trait B {
      |    def bMethod(): Unit = ???
      |  }
      |  trait API {
      |    implicit def a2b(a: A): B = ???
      |  }
      |  val api: API = ???
      |}
      |
      |object OwnerImpl extends Owner
      |
      |object Test {
      |  val a: OwnerImpl.A = ???
      |  a.${CARET}bMethod
      |}""".stripMargin,

    "OwnerImpl.api.a2b"
  )

  def testImplicitConversionOnGenericType(): Unit = doTest(
    fileText =
      s"""
         |object conversions {
         |  implicit class ObjectExt[T](private val v: T) extends AnyVal {
         |    def toOption: Option[T] = Option(v)
         |  }
         |}
         |class Test {
         |  def foo[T](t: T): Unit = {
         |    t.${CARET}toOption
         |  }
         |}
         |""".stripMargin,
    expectedText =
      """
        |import conversions.ObjectExt
        |
        |object conversions {
        |  implicit class ObjectExt[T](private val v: T) extends AnyVal {
        |    def toOption: Option[T] = Option(v)
        |  }
        |}
        |class Test {
        |  def foo[T](t: T): Unit = {
        |    t.toOption
        |  }
        |}""".stripMargin,

    "conversions.ObjectExt"
  )

  def testImplicitConversionOnGenericType1(): Unit = checkElementsToImport(
    s"""
       |object show {
       |  trait Show[T] {
       |    def show(t: T): String
       |  }
       |  trait Ops {
       |    def show: String = ???
       |  }
       |}
       |
       |object implicits {
       |  implicit def toShow[A](target: A)(implicit tc: show.Show[A]): show.Ops = ???
       |}
       |
       |class Test {
       |  def printShow[T](elem: T)(implicit show: show.Show[T]) {
       |    print(elem.${CARET}show)
       |  }
       |}""".stripMargin,

    "implicits.toShow"
  )

  def testImplicitConversionOnGenericType2(): Unit = checkElementsToImport(
    s"""
      |import scala.language.implicitConversions
      |trait DoubleParam[F[_], SubParam] {
      |  def foo2 = true
      |}
      |final class DoubleParamOps[F[_], SubParam, Val](private val p: F[Val])
      |  extends AnyVal {
      |  def foo2(implicit F: DoubleParam[F, SubParam]) = F.foo2
      |}
      |
      |object mySyntax {
      |  implicit def syntaxDoubleParam[F[_], SubParam, Val](
      |                                                       p: F[Val]
      |                                                     )(implicit F: DoubleParam[F, SubParam]) = {
      |    new DoubleParamOps[F, SubParam, Val](p)
      |  }
      |}
      |class Main {
      |  def f2[F[_]](x: F[Int])(implicit F: DoubleParam[F, Throwable]) = {
      |    x.${CARET}foo2
      |  }
      |}""".stripMargin,
  "mySyntax.syntaxDoubleParam")

  def testImplicitConversionOnObject(): Unit = doTest(
    fileText =
      s"""
         |object Tool
         |
         |object conversions {
         |  implicit class ObjectExt(private val tool: Tool.type) extends AnyVal {
         |    def toOption() = Option(tool)
         |  }
         |}
         |object Test {
         |  Tool.${CARET}toOption()
         |}
         |""".stripMargin,
    expectedText =
      """
        |import conversions.ObjectExt
        |
        |object Tool
        |
        |object conversions {
        |  implicit class ObjectExt(private val tool: Tool.type) extends AnyVal {
        |    def toOption() = Option(tool)
        |  }
        |}
        |object Test {
        |  Tool.toOption()
        |}""".stripMargin,

    "conversions.ObjectExt"
  )

  def testTopLevelConversion(): Unit = checkElementsToImport(
    s"""package tests
       |
       |package conversions {
       |  implicit class SeqExt[CC[X] <: Seq[X], A <: AnyRef](private val value: CC[A]) extends AnyVal {
       |    def firstBy[B](f: A => B)(implicit ord: Ordering[B]): Option[A] = ???
       |  }
       |}
       |
       |class Test {
       |  Nil.${CARET}firstBy()
       |}
       |""".stripMargin,

    "tests.conversions.SeqExt"
  )
}
