package org.jetbrains.plugins.hocon
package includes

import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.intellij.testFramework.LightPlatformTestCase.{getModule, getProject}
import com.intellij.testFramework.PsiTestUtil.removeContentEntry
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert.fail

class HoconSingleModuleIncludeResolutionTest extends LightPlatformCodeInsightTestCase with HoconIncludeResolutionTest {

  import HoconIncludeResolutionTest._

  protected def rootPath = s"${TestUtils.getTestDataPath}/hocon/includes/singlemodule"

  override def setUp(): Unit = {
    super.setUp()

    val rootModel = ModuleRootManager.getInstance(getModule).getModifiableModel
    val testDataRoot = contentRoot.getOrElse {
      fail()
      return
    }

    rootModel.addContentEntry(testDataRoot)
      .addSourceFolder(testDataRoot, false)

    inWriteAction {
      rootModel.commit()
    }
  }

  override def tearDown(): Unit = {
    contentRoot.foreach(removeContentEntry(getModule, _))
    super.tearDown()
  }

  def testIncludesFromTopLevel(): Unit =
    checkFile("including.conf")

  def testIncludesFromWithinPackage(): Unit =
    checkFile("pkg/including.conf")

  private def checkFile(path: String): Unit =
    checkFile(path, getProject)
}
