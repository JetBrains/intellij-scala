package org.jetbrains.plugins.scala
package annotator

import java.io.File

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.CharsetToolkit
import org.jetbrains.plugins.scala.util.TestUtils

class LiteralTypesHighlightingTestBase extends ScalaHighlightingTestBase {
  def folderPath = TestUtils.getTestDataPath + "/annotator/literalTypes/"

  override protected def supportedIn(version: ScalaVersion): Boolean = version <= Scala_2_12

  private def messageNoSupport(typeText: String): String = ScalaBundle.message("wrong.type.no.literal.types", typeText)

  override def errorsFromScalaCode(scalaFileText: String): List[Message] = {
    import org.jetbrains.plugins.scala.project._
    myFixture.getModule.scalaCompilerSettings.additionalCompilerOptions = Seq("-Yliteral-types")
    super.errorsFromScalaCode(scalaFileText)
  }

  def doTest(expectedErrors: List[Message] = Nil, fileText: Option[String] = None, settingOn: Boolean = false) {
    val text = fileText.getOrElse {
      val filePath = folderPath + getTestName(true) + ".scala"
      val ioFile: File = new File(filePath)
      FileUtil.loadFile(ioFile, CharsetToolkit.UTF8)
    }
    val errors = if (settingOn) errorsFromScalaCode(text) else super.errorsFromScalaCode(text)
    assertMessages(errors)(expectedErrors: _*)
  }

  def testDefaultIsOff(): Unit = {
    val expectedErrors =
      Error("-1", messageNoSupport("-1")) ::
        Error("1", messageNoSupport("1")) :: Nil

    doTest(expectedErrors,
      fileText = Some("""
                        |class O {
                        |  val x: -1 = -1
                        |  1: 1
                        |}
                      """.stripMargin))

  }

  def testSupportedWithFlag(): Unit = {
    val fileText = Some {
      """
        |object SimpleTest {
        |  val v: 42 = 42
        |}"""
        .stripMargin
    }
    doTest(fileText = fileText, settingOn = true)
  }
}
