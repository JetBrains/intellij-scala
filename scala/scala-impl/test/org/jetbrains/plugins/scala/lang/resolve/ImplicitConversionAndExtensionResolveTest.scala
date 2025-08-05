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
}
