package org.jetbrains.plugins.hocon.includes

import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.testFramework.LightPlatformTestCase
import org.jetbrains.plugins.hocon.HoconLightPlatformCodeInsightTestCase

class HoconIncludeResolutionTest extends HoconLightPlatformCodeInsightTestCase {

  import LightPlatformTestCase._

  override protected def rootPath = baseRootPath + "includes/"

  def testSimpleInclude(): Unit = {
    val mrm = ModuleRootManager.getInstance(getModule)
    val psim = getPsiManager

    val contentRoot = mrm.getContentRoots.head
    val firstFile = psim.findFile(contentRoot.findChild("first.conf"))
    val secondFile = psim.findFile(contentRoot.findChild("second.conf"))

    assert(secondFile.findReferenceAt(12).resolve() == firstFile)
    assert(secondFile.findReferenceAt(30).resolve() == firstFile)
  }
}
