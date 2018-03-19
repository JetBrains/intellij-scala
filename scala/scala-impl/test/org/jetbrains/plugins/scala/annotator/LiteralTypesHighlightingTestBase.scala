package org.jetbrains.plugins.scala.annotator

import java.io.File

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.CharsetToolkit
import org.jetbrains.plugins.scala.debugger.{ScalaVersion, Scala_2_13}

abstract class LiteralTypesHighlightingTestBase extends ScalaHighlightingTestBase {
  override implicit val version: ScalaVersion = Scala_2_13

  def folderPath: String

  override def errorsFromScalaCode(scalaFileText: String): List[Message] = {
    import org.jetbrains.plugins.scala.project._
    myFixture.getModule.scalaCompilerSettings.additionalCompilerOptions = Seq("-Yliteral-types")
    super.errorsFromScalaCode(scalaFileText)
  }

  def doTest(errorsFun: PartialFunction[List[Message], Unit] = PartialFunction.empty, fileText: Option[String] = None, settingOn: Boolean = true) {
    val text = fileText.getOrElse {
      val filePath = folderPath + getTestName(false) + ".scala"
      val ioFile: File = new File(filePath)
      FileUtil.loadFile(ioFile, CharsetToolkit.UTF8)
    }
    val errors = if (settingOn) errorsFromScalaCode(text) else super.errorsFromScalaCode(text)
    if (errorsFun == PartialFunction.empty) assertNothing(errors) else assertMatches(errors)(errorsFun)
  }
}
