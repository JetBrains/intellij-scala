package org.jetbrains.plugins.scala.lang.macros

import com.intellij.openapi.module.Module
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.base.libraryLoaders.IvyLibraryLoaderAdapter
import org.jetbrains.plugins.scala.debugger.{ScalaVersion, Scala_2_11}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.types.PhysicalSignature
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, Success}
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert._

/**
 * IntelliJ's equivalent of stalactite's built-in PresentationCompilerTest
 *
 * @author Sam Halliday
 * @since  24/08/2017
 */
class StalactiteTest extends ScalaLightCodeInsightFixtureTestAdapter {

  override implicit val version: ScalaVersion = Scala_2_11

  override def librariesLoaders =
    super.librariesLoaders :+
      StalactiteTest.StalactiteLoader() :+
      StalactiteTest.SimulacrumLoader()


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
        case PhysicalSignature(fun: ScFunctionDefinition, _) if fun.hasModifierProperty("implicit") => fun
      } match {
        case Some(method) =>
          method.returnType match {
            case Success(t, _) => assertEquals(s"${t.presentableText} != $expectedType", expectedType, t.presentableText)
            case Failure(cause, _) => fail(cause)
          }

        case None =>
          fail("no implicit def was generated")
      }
  }

  def testClass(): Unit = {
    val fileText: String = """
package wibble

import stalactite._
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

import stalactite._
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

import stalactite._
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

object StalactiteTest {

  case class StalactiteLoader()(implicit val module: Module) extends IvyLibraryLoaderAdapter {
    val vendor: String = "com.fommil"
    val name: String = "stalactite"
    val version: String = "0.0.3"
  }

  case class SimulacrumLoader()(implicit val module: Module) extends IvyLibraryLoaderAdapter {
    val vendor: String = "com.github.mpilquist"
    val name: String = "simulacrum"
    val version: String = "0.10.0"
  }
}
