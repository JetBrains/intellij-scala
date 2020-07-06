package org.jetbrains.plugins.scala.lang.autoImport

import org.jetbrains.plugins.scala.annotator.quickfix.ImportImplicitConversionFix
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class ImportConversionFixTest extends ImportElementFixTestBase[ScReferenceExpression] {
  //conversions from standard library may be different in older versions
  override protected def supportedIn(version: ScalaVersion) = version >= LatestScalaVersions.Scala_2_13

  override def createFix(ref: ScReferenceExpression) = ImportImplicitConversionFix(ref)

  def testAsJavaCollection(): Unit = checkElementsToImport(
    s"""object Test {
       |  Seq("").${CARET}asJavaCollection
       |}
       |""".stripMargin,
    "scala.jdk.CollectionConverters.IterableHasAsJava",
    "scala.collection.JavaConverters.asJavaCollectionConverter",
  )

  def testAsJava(): Unit = checkElementsToImport(
    s"""object Test {
       |  Seq("").${CARET}asJava
       |}
       |""".stripMargin,

    //todo reduce by fixing SCL-17791

    "scala.jdk.CollectionConverters.IterableHasAsJava",
    "scala.jdk.CollectionConverters.SeqHasAsJava",
    "scala.jdk.FunctionConverters.enrichAsJavaDoubleFunction",
    "scala.jdk.FunctionConverters.enrichAsJavaFunction",
    "scala.jdk.FunctionConverters.enrichAsJavaIntFunction",
    "scala.jdk.FunctionConverters.enrichAsJavaLongFunction",
    "scala.jdk.FunctionConverters.enrichAsJavaUnaryOperator",
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

  def testGenericImplicitClass(): Unit = checkElementsToImport(
    s"""object conversions {
       |  implicit class ObjectExt[T](private val v: T) extends AnyVal {
       |    def toOption: Option[T] = Option(v)
       |  }
       |}
       |class Test {
       |  "".${CARET}toOption
       |}
       |""".stripMargin,

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

}