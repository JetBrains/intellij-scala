package org.jetbrains.plugins.scala.lang.autoImport

import org.jetbrains.plugins.scala.autoImport.quickFix._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class ImportExtensionMethodFixTest
  extends ImportElementFixTestBase[ScReferenceExpression] {

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_3_0

  //noinspection InstanceOf
  override def createFix(ref: ScReferenceExpression): Option[ScalaImportElementFix[_ <: ElementToImport]] =
    ImportImplicitConversionFixes(ref).find(_.isInstanceOf[ImportExtensionMethodFix])

  def testPostfix(): Unit = checkElementsToImport(
    s"""object Extensions:
       |  extension (i: Int)
       |   def timesTen: Int = i * 10
       |end Extensions
       |
       |object Test:
       |  2 ${CARET}timesTen
       |""".stripMargin,

    "Extensions.timesTen"
  )

  def testInfix(): Unit = checkElementsToImport(
    s"""object Extensions:
       |  extension (i: Int)
       |   def times(n: Int): Int = i * n
       |end Extensions
       |
       |object Test:
       |  2 ${CARET}times n
       |""".stripMargin,

    "Extensions.times"
  )

  // TODO: `T | Null` is not supported yet. Change to `extension [T](x: T | Null)` when fixed
  def testGenericExtension(): Unit = checkElementsToImport(
    s"""object Extensions:
       |  extension [T](x: T)
       |    inline def toOption: Option[T] =
       |      if x == null then None else Some(x.asInstanceOf[T])
       |end Extensions
       |
       |"".${CARET}toOption
       |""".stripMargin,

    "Extensions.toOption"
  )

  def testExtensionOnGenericType(): Unit = checkElementsToImport(
    s"""object Extensions:
       |  extension [T](x: T)
       |    def toOption: Option[T] = Option(x)
       |
       |class Test:
       |  def foo[T](t: T): Unit =
       |    t.${CARET}toOption
       |""".stripMargin,

    "Extensions.toOption"
  )

  def testExtensionOnObject(): Unit = checkElementsToImport(
    s"""object Tool
       |
       |object Extensions:
       |  extension (tool: Tool.type)
       |    def toOption = Option(tool)
       |
       |object Test:
       |  Tool.${CARET}toOption
       |""".stripMargin,

    "Extensions.toOption"
  )

  def testGenericExtensionWithGenericMethodAndContextBound(): Unit = checkElementsToImport(
    s"""object Extensions:
       |  extension [T](xs: List[T])
       |    def sumBy[U: Numeric](f: T => U): U = ???
       |end Extensions
       |
       |List("a", "bb", "ccc").${CARET}sumBy[Int](_.length)
       |""".stripMargin,

    "Extensions.sumBy"
  )

  def testGenericExtensionWithBound(): Unit = checkElementsToImport(
    s"""trait Element
       |object MyElement extends Element
       |
       |object Extensions:
       |  extension [E <: Element](element: E)
       |    def doSomething(): Unit = ???
       |end Extensions
       |
       |class Test:
       |  MyElement.${CARET}doSomething()
       |""".stripMargin,

    "Extensions.doSomething"
  )

  def testStringInterpolation(): Unit = checkElementsToImport(
    s"""object StringInterpol:
       |  extension (sc: StringContext)
       |    def xy(args: Any*): BigDecimal = BigDecimal(sc.parts.head)
       |
       |class Test:
       |  ${CARET}xy"100500"
       |""".stripMargin,

    "StringInterpol.xy"
  )

  def testGenericExtensionWithTypeConstructor(): Unit = checkElementsToImport(
    s"""object Extensions:
       |  extension [CC[X] <: Seq[X], A <: AnyRef](cc: CC[A])
       |    def firstBy[B](f: A => B)(using ord: Ordering[B]): Option[A] = ???
       |
       |class Test:
       |  Nil.${CARET}firstBy()
       |""".stripMargin,

    "Extensions.firstBy"
  )

  def testFindMultipleExtensionMethodsInDifferentPackages(): Unit = checkElementsToImport(
    s"""package tests
       |
       |package ext1:
       |  object Extensions:
       |    extension (s: String)
       |      def toOption: Option[String] = Option(s)
       |end ext1
       |
       |package ext2:
       |  object Extensions:
       |    extension (s: String)
       |      def toOption: Option[String] = Option(s)
       |end ext2
       |
       |object Extensions:
       |  extension (s: String)
       |    def toOption: Option[String] = Option(s)
       |
       |"".${CARET}toOption
       |""".stripMargin,

    "tests.Extensions.toOption",
    "tests.ext1.Extensions.toOption",
    "tests.ext2.Extensions.toOption"
  )

  def testDoNotIncludeLocalExtensionOutsideOfItsScope(): Unit = checkNoImportFix(
    s"""object LocalExt:
       |  def foo[T](x: T): Unit = x match
       |  case s: String =>
       |    extension (str: String) def localExtensionMethod: Int = str.length * 2
       |  case _ =>
       |end LocalExt
       |
       |"".${CARET}localExtensionMethod
       |""".stripMargin
  )

  def testExtensionInsideGiven(): Unit = checkElementsToImport(
    s"""package tests
       |
       |final case class Context(things: Vector[String]) {
       |  def add(something: String) = Context(things :+ something)
       |}
       |
       |object Extensions:
       |  given ops: AnyRef with
       |    extension (c: Context)
       |      def addSomething(something: String) =
       |        c.add(something)
       |end Extensions
       |
       |val context = Context(Vector("foo", "bar"))
       |context.${CARET}addSomething("baz")
       |""".stripMargin,

    "tests.Extensions.ops"
  )

  def testIncludeTopLevelExtension(): Unit = checkElementsToImport(
    s"""package tests
       |
       |package ext:
       |  extension (s: String)
       |    def toOption: Option[String] = Option(s)
       |end ext
       |
       |"".${CARET}toOption
       |""".stripMargin,

    "tests.ext.toOption"
  )

}
