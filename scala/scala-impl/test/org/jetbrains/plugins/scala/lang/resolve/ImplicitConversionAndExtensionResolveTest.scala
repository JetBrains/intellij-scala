package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class ImplicitConversionAndExtensionResolveTest extends SimpleResolveTestBase {
  import SimpleResolveTestBase._

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_3_0

  def testExtensionsArePreferredToImplicits(): Unit = doResolveTest(
    s"""
       |object Blub {
       |  def run = {
       |    "foo" sho${REFSRC}uld "bar"
       |  }
       |
       |  extension [T](target: T)
       |    ${REFTGT}def should(p: String): Unit = ()
       |    def should(p: Boolean): Unit = ()
       |
       |  implicit class StringOps(private val target: String) extends AnyVal:
       |    def should(right: String): Unit = ()
       |}
       |
       |""".stripMargin
  )

  // SCL-19475
  def testMemberArgumentConstrainsContextBoundBeforeImplicitConversionIsApplied(): Unit = doResolveTest(
    s"""
       |class FooOps[F[_]] {
       |  ${REFTGT}def bar(value: F[Int]): Int = 123
       |}
       |
       |trait Monad[F[_]]
       |
       |import scala.language.implicitConversions
       |implicit def fooOps[A, F[_]: Monad](value: A): FooOps[F] = new FooOps[F]
       |
       |implicit val listMonad: Monad[List] = new Monad[List] {}
       |implicit val optionMonad: Monad[Option] = new Monad[Option] {}
       |
       |123.ba${REFSRC}r(Option(123))
       |""".stripMargin
  )

  // SCL-25859
  def testDirectReceiverExtensionIsPreferredToConversionAssistedReceiverExtension(): Unit = doResolveTest(
    s"""
       |class DirectReceiver
       |class ConvertedReceiver
       |
       |given Conversion[DirectReceiver, ConvertedReceiver] = null
       |
       |extension (receiver: ConvertedReceiver)
       |  def choose(argument: String): Unit = ()
       |
       |extension (receiver: DirectReceiver)
       |  ${REFTGT}def choose(argument: String): Unit = ()
       |
       |(null: DirectReceiver).cho${REFSRC}ose("")
       |""".stripMargin
  )

  def testDirectReceiverExtensionIsPreferredWithNonSymbolicSlashName(): Unit = doResolveTest(
    s"""
       |class DirectReceiver
       |class ConvertedReceiver
       |
       |given Conversion[DirectReceiver, ConvertedReceiver] = null
       |
       |extension (receiver: ConvertedReceiver)
       |  def slash(argument: String): Unit = ()
       |
       |extension (receiver: DirectReceiver)
       |  ${REFTGT}def slash(argument: String): Unit = ()
       |
       |(null: DirectReceiver).sla${REFSRC}sh("")
       |""".stripMargin
  )

  def testDirectGenericReceiverExtensionIsPreferredToConversionAssistedReceiverExtension(): Unit = doResolveTest(
    s"""
       |class DirectReceiver
       |class ConvertedReceiver
       |
       |given Conversion[DirectReceiver, ConvertedReceiver] = null
       |
       |extension (receiver: ConvertedReceiver)
       |  def choose(argument: String): Unit = ()
       |
       |extension [T](receiver: T)
       |  ${REFTGT}def choose(argument: String): Unit = ()
       |
       |(null: DirectReceiver).cho${REFSRC}ose("")
       |""".stripMargin
  )

  def testDirectSupertypeReceiverExtensionIsPreferredToConversionAssistedReceiverExtension(): Unit = doResolveTest(
    s"""
       |class DirectReceiverParent
       |class DirectReceiver extends DirectReceiverParent
       |class ConvertedReceiver
       |
       |given Conversion[DirectReceiver, ConvertedReceiver] = null
       |
       |extension (receiver: ConvertedReceiver)
       |  def choose(argument: String): Unit = ()
       |
       |extension (receiver: DirectReceiverParent)
       |  ${REFTGT}def choose(argument: String): Unit = ()
       |
       |(null: DirectReceiver).cho${REFSRC}ose("")
       |""".stripMargin
  )

  def testConversionAssistedReceiverExtensionRemainsAvailableWithoutDirectExtension(): Unit = doResolveTest(
    s"""
       |class OriginalReceiver
       |class ConvertedReceiver
       |
       |given Conversion[OriginalReceiver, ConvertedReceiver] = null
       |
       |extension (receiver: ConvertedReceiver)
       |  ${REFTGT}def choose(argument: String): Unit = ()
       |
       |(null: OriginalReceiver).cho${REFSRC}ose("")
       |""".stripMargin
  )

  def testInnerConversionAssistedExtensionKeepsPrecedenceOverOuterDirectExtension(): Unit = doResolveTest(
    s"""
       |class DirectReceiver
       |class ConvertedReceiver
       |
       |object Outer:
       |  extension (receiver: DirectReceiver)
       |    def choose(argument: String): Unit = ()
       |
       |  object Inner:
       |    given Conversion[DirectReceiver, ConvertedReceiver] = null
       |
       |    extension (receiver: ConvertedReceiver)
       |      ${REFTGT}def choose(argument: String): Unit = ()
       |
       |    (null: DirectReceiver).cho${REFSRC}ose("")
       |""".stripMargin
  )

  def testDirectReceiverExtensionIsPreferredWithSymbolicNameAndOldStyleConversion(): Unit = doResolveTest(
    s"""
       |class DirectReceiver
       |class ConvertedReceiver
       |
       |implicit def convert(receiver: DirectReceiver): ConvertedReceiver = null
       |
       |extension (receiver: ConvertedReceiver)
       |  def /(argument: String): Unit = ()
       |
       |extension (receiver: DirectReceiver)
       |  ${REFTGT}def /(argument: String): Unit = ()
       |
       |(null: DirectReceiver) ${REFSRC}/ ""
       |""".stripMargin
  )

  def testDirectSubtypeReceiverExtensionIsMoreSpecificThanDirectSupertypeReceiverExtension(): Unit = doResolveTest(
    s"""
       |class ParentReceiver
       |class ChildReceiver extends ParentReceiver
       |
       |extension (receiver: ParentReceiver)
       |  def choose(argument: String): Unit = ()
       |
       |extension (receiver: ChildReceiver)
       |  ${REFTGT}def choose(argument: String): Unit = ()
       |
       |(null: ChildReceiver).cho${REFSRC}ose("")
       |""".stripMargin
  )

  def testDirectReceiverExtensionPreventsConvertedReceiverFallbackWhenLaterArgumentIsWrong(): Unit = doResolveTest(
    s"""
       |class DirectReceiver
       |class ConvertedReceiver
       |
       |given Conversion[DirectReceiver, ConvertedReceiver] = null
       |
       |extension (receiver: DirectReceiver)
       |  ${REFTGT}def choose(argument: Int): Unit = ()
       |
       |extension (receiver: ConvertedReceiver)
       |  def choose(argument: String): Unit = ()
       |
       |(null: DirectReceiver).cho${REFSRC}ose("")
       |""".stripMargin
  )

  def testLaterArgumentConversionDoesNotLowerPriorityOfDirectReceiverExtension(): Unit = doResolveTest(
    s"""
       |class DirectReceiver
       |class ConvertedReceiver
       |class OriginalArgument
       |class ConvertedArgument
       |
       |given Conversion[DirectReceiver, ConvertedReceiver] = null
       |given Conversion[OriginalArgument, ConvertedArgument] = null
       |
       |extension (receiver: DirectReceiver)
       |  ${REFTGT}def choose(argument: ConvertedArgument): Unit = ()
       |
       |extension (receiver: ConvertedReceiver)
       |  def choose(argument: OriginalArgument): Unit = ()
       |
       |(null: DirectReceiver).cho${REFSRC}ose(null: OriginalArgument)
       |""".stripMargin
  )

  def testOneExtensionMethodDrownsOutImplicits(): Unit = doResolveTest(
    s"""
       |object Blub {
       |  def run = {
       |    "foo" sho${REFSRC}uld "bar"
       |  }
       |
       |  extension [T](target: T)
       |    ${REFTGT}def should(p: Boolean): Unit = ()
       |
       |  implicit class StringOps(private val target: String) extends AnyVal:
       |    def should(right: String): Unit = ()
       |}
       |
       |""".stripMargin
  )

  // SCL-23409
  def testMultipleExtensionMethodsDontDrownOutImplicits(): Unit = doResolveTest(
    s"""
       |object Blub {
       |  def run = {
       |    "foo" sho${REFSRC}uld "bar"
       |  }
       |
       |  extension [T](target: T)
       |    def should(p: Boolean): Unit = ()
       |    def should(p: Int): Unit = ()
       |
       |  implicit class StringOps(private val target: String) extends AnyVal:
       |    ${REFTGT}def should(right: String): Unit = ()
       |}
       |
       |""".stripMargin
  )

  def testImplicitConversionIsChosenIfExtensionsAreAmbiguous(): Unit = doResolveTest(
    s"""
       |object Blub {
       |  def run = {
       |    "foo" sho${REFSRC}uld "bar"
       |  }
       |
       |  extension [T](target: T)
       |    def should(p: String): Unit = ()
       |
       |  extension (target: String)
       |    def should(p: Boolean): Unit = ()
       |    def should(p: String): Unit = ()
       |
       |  implicit class StringOps[T](private val target: T) extends AnyVal:
       |    ${REFTGT}def should(right: String): Unit = ()
       |    def should(right: Int): Unit = ()
       |}
       |""".stripMargin
  )

  def testUnspecificExtensionHasHigherPrecedenceThanImplicitConversion(): Unit = doResolveTest(
    s"""
       |object Blub {
       |  def run = {
       |    "foo" sho${REFSRC}uld "bar"
       |  }
       |
       |  extension [T](target: T)
       |    def should(p: Boolean): Unit = ()
       |    ${REFTGT}def should(p: String): Unit = ()
       |
       |  extension (target: String)
       |    def should(p: Boolean): Unit = ()
       |    def should(p: Int): Unit = ()
       |
       |  implicit class StringOps(private val target: String) extends AnyVal:
       |    def should(right: String): Unit = ()
       |}
       |
       |""".stripMargin
  )

  def testInnerUnspecificExtensionHasHigherPrecedenceThanOuterSpecificExtension(): Unit = doResolveTest(
    s"""
       |object Blub {
       |  object Inner {
       |    def run = {
       |      "foo" sho${REFSRC}uld "bar"
       |    }
       |
       |    extension [T](target: T)
       |      def should(p: Boolean): Unit = ()
       |      ${REFTGT}def should(p: String): Unit = ()
       |  }
       |
       |  extension (target: String)
       |    def should(p: Boolean): Unit = ()
       |    def should(p: String): Unit = ()
       |
       |  implicit class StringOps(private val target: String) extends AnyVal:
       |    def should(right: String): Unit = ()
       |}
       |""".stripMargin
  )

  def testOneWrongSpecificExtensionMethodDrownsOutEverythingElse(): Unit = doResolveTest(
    s"""
       |// In this case the compiler first tries to resolve the ambiguity by checking
       |//   should("foo")
       |// Because
       |//   String => Boolean => Unit
       |// is more specific than
       |//   [T] T => String => Unit
       |// it is chosen even if the second argument does not match.
       |object Blub {
       |  def run = {
       |    "foo" sho${REFSRC}uld "bar"
       |  }
       |
       |  extension [T](target: T)
       |    def should(p: String): Unit = ()
       |
       |  extension (target: String)
       |    ${REFTGT}def should(p: Boolean): Unit = ()  // Resolves to here even though there is a type mismatch for the argument
       |
       |  implicit class StringOps(private val target: String) extends AnyVal:
       |    def should(right: String): Unit = ()
       |}
       |
       |""".stripMargin
  )

  def testNormalMethodHasHighestPrecedence(): Unit = doResolveTest(
    s"""
       |
       |object Blub {
       |  class Test {
       |    ${REFTGT}def should(s: String): Unit = ()
       |  }
       |  object Test extends Test
       |
       |  def run = {
       |    Test sho${REFSRC}uld "bar"
       |  }
       |
       |  extension (target: Test)
       |    def should(p: String): Unit = ()
       |
       |  implicit class TestOps(private val target: Test) extends AnyVal:
       |    def should(right: String): Unit = ()
       |}
       |
       |""".stripMargin
  )

  def testIfOneExtensionIsApplicableThenFallbackToTypesOwnMethod(): Unit = doResolveTest(
    s"""
       |// The implicit conversion is not even considered
       |// because there was exactly one extension method.
       |// Because that one had application issues the compiler
       |// falls back to the original Test.should
       |
       |object Blub {
       |  class Test {
       |    ${REFTGT}def should(s: Int): Unit = ()
       |  }
       |  object Test extends Test
       |
       |  def run = {
       |    Test sho${REFSRC}uld "bar"
       |  }
       |
       |  extension (target: Test)
       |    def should(p: Boolean): Unit = ()
       |
       |  implicit class TestOps(private val target: Test) extends AnyVal:
       |    def should(right: String): Unit = ()
       |}
       |
       |""".stripMargin
  )

  def testExtensionInAbstractTypeCompanionInObject(): Unit = doResolveTest(
    s"""
       |object O:
       |  type T
       |  object T:
       |    extension (x: T) ${REFTGT}def foo = ???
       |  def bar = (??? : T).fo${REFSRC}o
       |""".stripMargin)

  def testExtensionInAbstractTypeCompanionInPackage(): Unit = doResolveTest(
    s"""
       |package p
       |type T
       |object T:
       |  extension (x: T) ${REFTGT}def foo = ???
       |def bar = (??? : T).fo${REFSRC}o
       |""".stripMargin)

  def testExtensionInAbstractTypeCompanionInEmptyPackage(): Unit = doResolveTest(
    s"""
       |type T
       |object T:
       |  extension (x: T) ${REFTGT}def foo = ???
       |def bar = (??? : T).fo${REFSRC}o
       |""".stripMargin)
}
