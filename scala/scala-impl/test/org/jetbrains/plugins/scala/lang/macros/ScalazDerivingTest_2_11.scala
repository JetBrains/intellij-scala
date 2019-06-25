package org.jetbrains.plugins.scala
package lang
package macros

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.base.libraryLoaders.IvyManagedLoader
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.types.PhysicalMethodSignature
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert._

/**
 * IntelliJ's equivalent of scalaz-deriving's built-in PresentationCompilerTest
 *
 * @author Sam Halliday
 * @since  24/08/2017
 */
class ScalazDerivingTest_2_11 extends ScalaLightCodeInsightFixtureTestAdapter {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == Scala_2_11

  override def librariesLoaders = super.librariesLoaders :+ IvyManagedLoader(
    "com.fommil"           %% "stalactite" % "0.0.5",
    "com.github.mpilquist" %% "simulacrum" % "0.11.0"
  )


  protected def folderPath: String = TestUtils.getTestDataPath

  def doTest(text: String, expectedType: String): Unit = {
    val cleaned = StringUtil.convertLineSeparators(text)
    val caretPos = cleaned.indexOf("<caret>")
    getFixture.configureByText("dummy.scala", cleaned.replace("<caret>", ""))

    val clazz = PsiTreeUtil.findElementOfClassAtOffset(
      getFile,
      caretPos,
      classOf[ScTypeDefinition],
      false
    )

    clazz
      .fakeCompanionModule
      .getOrElse(clazz.asInstanceOf[ScObject])
      .allMethods
      .collectFirst {
        case PhysicalMethodSignature(fun: ScFunctionDefinition, _) if fun.hasModifierProperty("implicit") => fun
      } match {
        case Some(method) =>
          method.returnType match {
            case Right(t) => assertEquals(s"${t.presentableText} != $expectedType", expectedType, t.presentableText)
            case Failure(cause) => fail(cause)
          }

        case None =>
          fail("no implicit def was generated")
      }
  }

  def testClass(): Unit = {
    val fileText: String = """
package wibble

import stalactite.deriving
import simulacrum.typeclass

@typeclass trait Wibble[T] {}
object DerivedWibble {
  def gen[T]: Wibble[T] = new Wibble[T] {}
}

@deriving(Wibble)
final case class <caret>Foo(string: String, int: Int)
"""

    doTest(fileText, "Wibble[Foo]")
  }

  def testTrait(): Unit = {
    val fileText: String = """
package wibble

import stalactite.deriving
import simulacrum.typeclass

@typeclass trait Wibble[T] {}
object DerivedWibble {
  def gen[T]: Wibble[T] = new Wibble[T] {}
}

@deriving(Wibble)
sealed trait <caret>Baz

@deriving(Wibble)
final case class Foo(string: String, int: Int) extends Baz
"""

    doTest(fileText, "Wibble[Baz]")
  }

  def testObject(): Unit = {
    val fileText: String = """
package wibble

import stalactite.deriving
import simulacrum.typeclass

@typeclass trait Wibble[T] {}
object DerivedWibble {
  def gen[T]: Wibble[T] = new Wibble[T] {}
}

@deriving(Wibble)
case object <caret>Caz
"""

    doTest(fileText, "Wibble[Caz.type]")
  }

}
