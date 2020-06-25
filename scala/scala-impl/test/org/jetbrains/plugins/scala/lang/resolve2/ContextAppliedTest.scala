package org.jetbrains.plugins.scala.lang.resolve2

import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration

/**
 * Yannick Heiber, 16.02.2020
 */

class ContextAppliedTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "function/contextApplied/"
  }


  override def setUp(): Unit = {
    super.setUp()

    val defaultProfile = ScalaCompilerConfiguration.instanceIn(getProjectAdapter).defaultProfile
    val newSettings = defaultProfile.getSettings.copy(
      plugins = defaultProfile.getSettings.plugins :+ "context-applied"
    )
    defaultProfile.setSettings(newSettings)
  }

  def testFunctionSingleBound() = doTest()

  def testClassSingleBound() = doTest()

  def testFunctionWithExistingParam() = doTest()

  def testAnyValClassSingleBound() = doTest()

  def testFunctionMultiBound() = doTest()

  def testFunctionMultiBoundOrder() = doTest()

}
