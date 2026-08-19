package org.jetbrains.plugins.scala.lang.resolve2

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaSdkOwner
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel

import java.nio.file.Path

class FunctionTypeGenericTest extends ResolveTestBase {
  override def folderPath: Path = super.folderPath / "function" / "type" / "generic"

  def testFunction2(): Unit = doTest()
  //TODO answer?
//  def testFunctionExpression1 = doTest
  def testFunctionExpression2(): Unit = doTest()
  //TODO answer?
//  def testGeneric1 = doTest
  def testGeneric2(): Unit = doTest()
}

/**
 * The code in `Function1.scala` doesn't compile in Scala 2.12 (it does in 2.11 and in 2.13+):
 * when a function literal with a missing parameter type is passed to an overloaded method,
 * scalac 2.12 builds the expected type by lub-ing the parameter types of all alternatives
 * (`functionProto` in `scala.tools.nsc.typechecker.Typers`), so in
 * {{{
 *   def f(p: String => Unit) {}
 *   def f(p: Int => Unit)(x: String) {}
 *   f(b(_))("")
 * }}}
 * the placeholder gets the type `lub(String, Int) = Any` and scalac reports
 * "type mismatch; found: Any, required: Int".
 *
 * We model this in [[org.jetbrains.plugins.scala.lang.psi.impl.expr.ExpectedTypesImpl]]
 * (see `canLubParamTpes`), which is why `f` resolves to both alternatives in 2.12.
 */
class FunctionTypeGenericTest_without_ParamTypeLubbing extends ResolveTestBase {
  override def folderPath: Path = super.folderPath / "function" / "type" / "generic"

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version.languageLevel != ScalaLanguageLevel.Scala_2_12

  def testFunction1(): Unit = doTest()
}
