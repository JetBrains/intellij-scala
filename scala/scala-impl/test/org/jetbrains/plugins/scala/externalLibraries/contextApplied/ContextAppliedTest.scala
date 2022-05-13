package org.jetbrains.plugins.scala.externalLibraries.contextApplied

import org.jetbrains.plugins.scala.lang.resolve.SimpleResolveTestBase
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration

class ContextAppliedTest extends SimpleResolveTestBase {
  import SimpleResolveTestBase._

  override def setUp(): Unit = {
    super.setUp()

    val defaultProfile = ScalaCompilerConfiguration.instanceIn(getProject).defaultProfile
    val newSettings = defaultProfile.getSettings.copy(
      plugins = defaultProfile.getSettings.plugins :+ "context-applied"
    )
    defaultProfile.setSettings(newSettings)
  }

  def testAnyValClassSingleBound(): Unit = testNoResolve(
    s"""
       |trait Log[F[_]] {
       |  def log(msg: String): F[Unit]
       |}
       |
       |class Foo(val dummy: Boolean) extends AnyVal {
       |  def bar[T[_]: Log] = T.l${REFSRC}og("Hello")
       |}
       |""".stripMargin -> "Foo.scala"
  )

  def testClassSingleBound(): Unit = doResolveTest(
    s"""
       |trait Log[F[_]] {
       |  def log(msg: String): F[Unit]
       |}
       |
       |class foo[T[_]: Log]() {
       |  T.l${REFSRC}og("Hello")
       |}
       |""".stripMargin
  )

  def testFunctionMultiBound(): Unit = doResolveTest(
    s"""
       |trait Log[F[_]] {
       |  def log(msg: String): F[Unit]
       |}
       |
       |trait Console[F[_]] {
       |  def read: F[Unit]
       |}
       |
       |def foo[T[_]: Log : Console] = {
       |  T.l${REFSRC}og("Hello")
       |  T.r${REFSRC}ead
       |}
       |""".stripMargin
  )

  def testFunctionMultiBoundOrder(): Unit = doResolveTest(
    s"""
      |trait Base[F[_]] {
      |  def go(): Unit
      |}
      |
      |trait First[F[_]] extends Base[F] {
      |  def ${REFTGT}go(): Unit = println("First")
      |}
      |
      |trait Second[F[_]] extends Base[F] {
      |  def go(): Unit = println("Second")
      |}
      |
      |def foo[T[_]: First : Second] = T.g${REFSRC}o()
      |""".stripMargin
  )

  def testFunctionMultiBoundOrderReversed(): Unit = doResolveTest(
    s"""
      |trait Base[F[_]] {
      |  def go(): Unit
      |}
      |
      |trait First[F[_]] extends Base[F] {
      |  def go(): Unit = println("First")
      |}
      |
      |trait Second[F[_]] extends Base[F] {
      |  def g${REFTGT}o(): Unit = println("Second")
      |}
      |
      |def foo[T[_]: Second : First] = T.g${REFSRC}o()
      |""".stripMargin
  )

  def testFunctionSingleBound(): Unit = doResolveTest(
    s"""
       |trait Log[F[_]] {
       |  def log(msg: String): F[Unit]
       |}
       |
       |def foo[T[_]: Log] = T.l${REFSRC}og("Hello")
       |""".stripMargin
  )

  def testFunctionWithExistingParam(): Unit = testNoResolve(
    s"""
       |trait Log[F[_]] {
       |  def log(msg: String): F[Unit]
       |}
       |
       |def foo[T[_]: Log](T: Unit) = T.l${REFSRC}og("Hello")
       |""".stripMargin -> "Foo.scala"
  )

  def testClassWithConstructorParam(): Unit = testNoResolve(
    s"""
       |trait Log[F[_]] {
       |  def log(msg: String): F[Unit]
       |}
       |
       |class foo[T[_]: Log](T: Int) {
       |  T.l${REFSRC}og("Hello")
       |}""".stripMargin -> "Foo.scala"
  )
}
