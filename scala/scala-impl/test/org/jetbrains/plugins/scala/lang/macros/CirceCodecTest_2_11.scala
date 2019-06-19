package org.jetbrains.plugins.scala.lang.macros

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.debugger.{ScalaVersion, Scala_2_11}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.types.PhysicalMethodSignature
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert._


class CirceCodecTest_2_11 extends ScalaLightCodeInsightFixtureTestAdapter {

  override implicit val version: ScalaVersion = Scala_2_11

  override def librariesLoaders: Seq[LibraryLoader] = super.librariesLoaders :+ IvyManagedLoader(
    "io.circe" %% "circe-core" % "0.9.3",
    "io.circe" %% "circe-generic" % "0.9.3",
    "io.circe" %% "circe-generic-extras" % "0.9.3"
  )

  protected def folderPath: String = TestUtils.getTestDataPath

  def doTest(text: String, codecTypeName: String): Unit = {
    val cleaned = StringUtil.convertLineSeparators(text)
    val caretPos = cleaned.indexOf("<caret>")
    getFixture.configureByText("dummy.scala", cleaned.replace("<caret>", ""))

    val clazz = PsiTreeUtil.findElementOfClassAtOffset(
      getFile,
      caretPos,
      classOf[ScTypeDefinition],
      false
    )

    val implicitDefs0: Set[TypeResult] = ScalaPsiUtil.getCompanionModule(clazz)
      .getOrElse(clazz.asInstanceOf[ScObject])
      .allMethods
      .collect {
        case PhysicalMethodSignature(fun: ScFunctionDefinition, _) if fun.hasModifierProperty("implicit") => fun
      }
      .map(m => m.returnType)
      .toSet

    assertTrue("no implicit defs were generated", implicitDefs0.forall(_.isRight))

    val implicitDefs: Set[String] =
      implicitDefs0.map(_.right.get.presentableText)

    assertEquals(
      s"$implicitDefs != Set(Encoder[$codecTypeName], Decoder[$codecTypeName])",
      implicitDefs,
      Set(
        s"Encoder[$codecTypeName]",
        s"Decoder[$codecTypeName]"
      )
    )
  }

  def testJsonCodec(): Unit = {
    val fileText: String = """
package foo

import io.circe.generic.JsonCodec

@JsonCodec case class <caret>A(x: Int)
"""

    doTest(fileText, "A")
  }

  def testConfiguredJsonCodec(): Unit = {
    val fileText: String = """
package foo

import io.circe.generic.extras.ConfiguredJsonCodec

@ConfiguredJsonCodec case class <caret>B(x: Int)
"""

    doTest(fileText, "B")
  }

  def testGenericClasses0(): Unit = {
    val fileText: String = """
package foo

import io.circe.generic.JsonCodec

@JsonCodec case class <caret>C[A: Encoder: Decoder](x: A)
"""

    doTest(fileText, "C[A]")
  }

  def testGenericClasses1(): Unit = {
    val fileText: String = """
package foo

import io.circe.generic.JsonCodec

@JsonCodec case class <caret>D[A: Encoder: Decoder, B: Encoder: Decoder](x: A, y: B)
"""

    doTest(fileText, "D[A, B]")
  }

  def testObject(): Unit = {
    val fileText: String = """
package foo

import io.circe.generic.JsonCodec

@JsonCodec case object <caret>X
"""

    doTest(fileText, "X.type")
  }

  def testTrait(): Unit = {
    val fileText: String = """
package foo

import io.circe.generic.JsonCodec

@JsonCodec sealed trait <caret>Y
"""

    doTest(fileText, "Y")
  }
  
  def testCaseClassWithCompanion(): Unit = {
    val fileText: String = """
package foo

import io.circe.generic.JsonCodec

@JsonCodec case class <caret>Boom(i: Int)
object Boom {
  val something: String = ""
}
"""

    doTest(fileText, "Boom")
  }

}
