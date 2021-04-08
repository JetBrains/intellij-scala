package org.jetbrains.plugins.scala
package lang
package typeInference

import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.base.{ScalaLightCodeInsightFixtureTestAdapter, SharedTestProjectToken}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.SyntheticMembersInjector
import org.jetbrains.plugins.scala.util.{PsiFileTestUtil, TestUtils}
import org.junit.experimental.categories.Category

import java.io.File
import scala.annotation.nowarn

/**
  * User: Alexander Podkhalyuzin
  * Date: 10.03.2009
  */
@Category(Array(classOf[TypecheckerTests]))
abstract class TypeInferenceTestBase extends ScalaLightCodeInsightFixtureTestAdapter with TypeInferenceDoTest {

  override val START = "/*start*/"
  override val END = "/*end*/"

  protected def folderPath: String = TestUtils.getTestDataPath + "/typeInference/"

  override protected def sharedProjectToken = SharedTestProjectToken(this.getClass)

  protected def doInjectorTest(injector: SyntheticMembersInjector): Unit = {
    val extensionPoint = Extensions.getRootArea.getExtensionPoint(SyntheticMembersInjector.EP_NAME): @nowarn("cat=deprecation")
    extensionPoint.registerExtension(injector): @nowarn("cat=deprecation")
    try {
      doTest()
    } finally {
      extensionPoint.unregisterExtension(injector): @nowarn("cat=deprecation")
    }
  }

  override def configureFromFileText(fileName: String, fileText: Option[String]): ScalaFile = {
    val text = fileText.getOrElse {
      val filePath = folderPath + fileName
      val ioFile: File = new File(filePath)
      FileUtil.loadFile(ioFile, CharsetToolkit.UTF8)
    }
    configureFromFileText(StringUtil.convertLineSeparators(text.trim), ScalaFileType.INSTANCE)
    getFile.asInstanceOf[ScalaFile]
  }

  protected def addFileToProject(fileName: String, text: String): PsiFile =
    PsiFileTestUtil.addFileToProject(fileName, text, getProject)

  protected def doTest(): Unit = doTest(None, getTestName(false) + ".scala")
}