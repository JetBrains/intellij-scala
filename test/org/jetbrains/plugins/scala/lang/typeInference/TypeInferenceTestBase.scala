package org.jetbrains.plugins.scala
package lang
package typeInference

import java.io.File

import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.SyntheticMembersInjector
import org.jetbrains.plugins.scala.lang.psi.types.api.Unit
import org.jetbrains.plugins.scala.lang.psi.types.api.ScTypePresentation
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, Success, TypingContext}
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert._

/**
 * User: Alexander Podkhalyuzin
 * Date: 10.03.2009
 */

abstract class TypeInferenceTestBase extends ScalaLightPlatformCodeInsightTestCaseAdapter with TypeInferenceDoTest {
  protected def folderPath: String = TestUtils.getTestDataPath + "/typeInference/"

  protected def doInjectorTest(injector: SyntheticMembersInjector): Unit = {
    val extensionPoint = Extensions.getRootArea.getExtensionPoint(SyntheticMembersInjector.EP_NAME)
    extensionPoint.registerExtension(injector)
    try {
      doTest()
    } finally {
      extensionPoint.unregisterExtension(injector)
    }
  }

  def configureFromFileText(fileName: String, fileText: String): ScalaFile = {
    configureFromFileTextAdapter(fileName, fileText)
    getFileAdapter.asInstanceOf[ScalaFile]
  }

  protected def doTest() {
    val filePath = folderPath + getTestName(false) + ".scala"
    val ioFile: File = new File(filePath)
    var fileText: String = FileUtil.loadFile(ioFile, CharsetToolkit.UTF8)
    fileText = StringUtil.convertLineSeparators(fileText)
    doTest(fileText, ioFile.getName)
  }
}