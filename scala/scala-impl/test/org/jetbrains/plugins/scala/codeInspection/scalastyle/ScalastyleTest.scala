package org.jetbrains.plugins.scala.codeInspection.scalastyle

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.{VfsUtil, VirtualFile}
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase

class ScalastyleTest extends ScalaInspectionTestBase {

  val config =
    <scalastyle commentFilter="enabled">
      <name>Scalastyle standard configuration</name>
      <check level="warning" class="org.scalastyle.scalariform.ClassNamesChecker" enabled="true">
        <parameters>
          <parameter name="regex">[A-Z][A-Za-z]*</parameter>
        </parameters>
      </check>
    </scalastyle>

  val configString = config.toString()

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[ScalastyleCodeInspection]

  override protected val description = "Class name does not match the regular expression '[A-Z][A-Za-z]*'."

  private def setup(): Unit = {
    myFixture.addFileToProject("scalastyle-config.xml", configString)
  }

  def test_ok(): Unit = {
    setup()

    checkTextHasNoErrors(
      """
        |class Test
      """.stripMargin
    )
  }

  def test(): Unit = {
    setup()

    checkTextHasError(
      s"""
         |${START}class test$END
      """.stripMargin
    )
  }

  def testFallback(): Unit = {
    def getOrCreateFile(dir: VirtualFile, file: String): VirtualFile =
      Option(dir.findChild(file)).getOrElse(dir.createChildData(this, file))

    WriteAction.runAndWait { () =>
      val baseDir = VfsUtil.createDirectoryIfMissing(getProject.getBasePath)
      val file = getOrCreateFile(baseDir, "scalastyle-config.xml")
      VfsUtil.saveText(file, configString)
    }

    checkTextHasError(
      s"""
         |${START}class test$END
      """.stripMargin
    )
  }
}
