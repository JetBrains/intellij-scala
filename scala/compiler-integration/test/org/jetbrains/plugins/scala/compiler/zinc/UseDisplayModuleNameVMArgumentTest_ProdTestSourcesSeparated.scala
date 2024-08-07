package org.jetbrains.plugins.scala.compiler.zinc

class UseDisplayModuleNameVMArgumentTest_ProdTestSourcesSeparated extends DisplayModuleNameTestBase(separateProdAndTestSources = true) {

  def testSingleBuild(): Unit = {
    createSingleBuildProject()
    runTest(true)
  }

  def testMultipleBuildsWithUniqueNames(): Unit = {
    createMultipleBuildsProjectWithUniqueNames()
    runTest(true)
  }

  def testMultipleBuildsWithDuplicatedNames(): Unit = {
    createMultipleBuildsProjectWithDuplicatedNames()
    runTest(false)
  }
}
