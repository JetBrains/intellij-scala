package org.jetbrains.plugins.scala
package lang
package typeInference

import java.io.File

import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.debugger.ScalaVersion
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.SyntheticMembersInjector
import org.jetbrains.plugins.scala.util.{PsiFileTestUtil, TestUtils}
import org.junit.experimental.categories.Category

/**
  * User: Alexander Podkhalyuzin
  * Date: 10.03.2009
  */

@Category(Array(classOf[TypecheckerTests]))
abstract class TypeInferenceTestBase extends ScalaLightPlatformCodeInsightTestCaseAdapter with TypeInferenceDoTest {
  protected def folderPath: String = TestUtils.getTestDataPath + "/typeInference/"
  
  implicit def scalaVersion: ScalaVersion = version()

  protected def doInjectorTest(injector: SyntheticMembersInjector): Unit = {
    val extensionPoint = Extensions.getRootArea.getExtensionPoint(SyntheticMembersInjector.EP_NAME)
    extensionPoint.registerExtension(injector)
    try {
      doTest()
    } finally {
      extensionPoint.unregisterExtension(injector)
    }
  }

  def configureFromFileText(fileName: String, fileText: Option[String]): ScalaFile = {
    val text = fileText.getOrElse {
      val filePath = folderPath + fileName
      val ioFile: File = new File(filePath)
      FileUtil.loadFile(ioFile, CharsetToolkit.UTF8)
    }
    configureFromFileTextAdapter(fileName, StringUtil.convertLineSeparators(text.trim))
    getFileAdapter.asInstanceOf[ScalaFile]
  }

  protected def addFileToProject(fileName: String, text: String): PsiFile =
    PsiFileTestUtil.addFileToProject(fileName, text, getProjectAdapter)

  protected def doTest(): Unit = doTest(None, getTestName(false) + ".scala")
}