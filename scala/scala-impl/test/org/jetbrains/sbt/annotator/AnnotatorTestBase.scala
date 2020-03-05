package org.jetbrains.sbt
package annotator

import java.io.File

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import com.intellij.testFramework.PlatformTestCase
import org.jetbrains.plugins.scala.util.TestUtils.getTestDataPath
import org.junit.Assert._

/**
 * @author Nikolay Obedin
 * @since 8/4/14.
 */
abstract class AnnotatorTestBase extends PlatformTestCase {

  protected def testdataPath: String = s"$getTestDataPath/annotator/Sbt"

  def loadTestFile() = {
    val filePath = s"$testdataPath/${getTestName(false)}${Sbt.Extension}"
    val file = LocalFileSystem.getInstance
      .findFileByPath(filePath.replace(File.separatorChar, '/'))
    assertNotNull(filePath, file)

    PsiManager.getInstance(getProject)
      .findFile(file)
      .asInstanceOf[language.SbtFileImpl]
  }
}
