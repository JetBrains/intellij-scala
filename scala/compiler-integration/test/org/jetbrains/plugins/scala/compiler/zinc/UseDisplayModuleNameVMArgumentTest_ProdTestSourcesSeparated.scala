package org.jetbrains.plugins.scala.compiler.zinc

import org.jetbrains.plugins.scala.CompilationTests
import org.junit.experimental.categories.Category

@Category(Array(classOf[CompilationTests]))
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
