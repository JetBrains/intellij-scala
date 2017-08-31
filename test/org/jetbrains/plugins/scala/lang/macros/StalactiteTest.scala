package org.jetbrains.plugins.scala.lang.macros

import com.intellij.openapi.module.Module
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.base.libraryLoaders.{ IvyLibraryLoaderAdapter, ThirdPartyLibraryLoader }
import org.jetbrains.plugins.scala.debugger.{ ScalaVersion, Scala_2_11 }
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.types.result.{ Failure, Success }
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert._

/**
 * IntelliJ's equivalent of stalactite's built-in PresentationCompilerTest
 *
 * @author Sam Halliday
 * @since  24/08/2017
 */
class StalactiteTest extends ScalaLightPlatformCodeInsightTestCaseAdapter {

  override implicit val version: ScalaVersion = Scala_2_11

  override protected def additionalLibraries(): Array[ThirdPartyLibraryLoader] = {
    implicit val module = getModuleAdapter
    Array(StalactiteTest.StalactiteLoader())
  }

  protected def folderPath: String = TestUtils.getTestDataPath

  def doTest(text: String, expectedType: String) = {
    val caretPos = text.indexOf("<caret>")
    configureFromFileTextAdapter("dummy.scala", text.replace("<caret>", ""))

    val clazz = PsiTreeUtil
      .findElementOfClassAtOffset(
        getFileAdapter,
        caretPos,
        classOf[ScalaPsiElement],
        false)
      .asInstanceOf[ScTypeDefinition]

    clazz
      .fakeCompanionModule
      .getOrElse(clazz.asInstanceOf[ScObject])
      .allMethods
      .collect {
        case sig if sig.method.isInstanceOf[ScFunctionDefinition] => sig.method.asInstanceOf[ScFunctionDefinition]
      }
      .filter(_.getText.contains("implicit def")) // hacky
      .headOption match {
        case Some(method) =>
          method.returnType match {
            case Success(t, _) => assertEquals(s"${t.toString} != $expectedType", expectedType, t.toString)
            case Failure(cause, _) => fail(cause)
          }

        case None =>
          fail("no implicit def was generated")
      }
  }

  def testClass = {
    val fileText: String = """
package wibble

import stalactite._

@typeclass trait Wibble[T] {}
object DerivedWibble {
  def gen[T]: Wibble[T] = new Wibble[T] {}
}

@deriving(Wibble)
final case class <caret>Foo(string: String, int: Int)
"""

    doTest(fileText, "Wibble[Foo]")
  }

  def testTrait = {
    val fileText: String = """
package wibble

import stalactite._

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

  def testObject = {
    val fileText: String = """
package wibble

import stalactite._

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

  case class StalactiteLoader(
    version: String = "0.0.2",
    vendor: String = "com.fommil",
    name: String = "stalactite")(implicit val module: Module) extends IvyLibraryLoaderAdapter

}
