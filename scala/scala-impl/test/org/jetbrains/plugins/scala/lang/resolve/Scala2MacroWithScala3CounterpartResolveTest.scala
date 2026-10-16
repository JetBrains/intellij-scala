package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

/**
 * Libraries cross-built for Scala 2.13 and Scala 3 define a Scala 2 macro alongside its Scala 3 counterpart
 * with the same signature, e.g. an inline method. Scala 3 code uses the Scala 3 counterpart, while Scala 2 code
 * (which reads the library from TASTy) uses the macro, so which of the two a reference resolves to depends on
 * the language of the place of the reference.
 *
 * Only one member per signature is kept in [[org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.MixinNodes]],
 * the one defined first, and that's usually the macro, so references in Scala 3 code resolve to the macro.
 */
class Scala2MacroWithScala3CounterpartResolveTest extends SimpleResolveTestBase {
  import SimpleResolveTestBase._

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_3

  override protected def shouldPass: Boolean = false

  def testQualifiedReference(): Unit = doResolveTest(
    s"""import scala.language.experimental.macros
       |
       |object Macros {
       |  def lineNumber: Int = macro lineNumberImpl
       |  inline def ${REFTGT}lineNumber: Int = 4
       |
       |  def lineNumberImpl(c: Any): Any = ???
       |}
       |
       |val a = Macros.${REFSRC}lineNumber
       |""".stripMargin
  )

  def testReferenceInTheSameObject(): Unit = doResolveTest(
    s"""import scala.language.experimental.macros
       |
       |object Macros {
       |  def foo(x: Int): Int = macro fooImpl
       |  def ${REFTGT}foo(x: Int): Int = ???
       |
       |  def fooImpl(c: Any)(x: Any): Any = ???
       |
       |  def bar: Int = ${REFSRC}foo(1)
       |}
       |""".stripMargin
  )

  def testInheritedMember(): Unit = doResolveTest(
    s"""import scala.language.experimental.macros
       |
       |trait Logging {
       |  def info(message: String): Unit = macro Logging.infoImpl
       |  inline def ${REFTGT}info(message: String): Unit = println(message)
       |}
       |
       |object Logging {
       |  def infoImpl(c: Any)(message: Any): Any = ???
       |}
       |
       |object Service extends Logging
       |
       |def test(): Unit = Service.${REFSRC}info("started")
       |""".stripMargin
  )
}
