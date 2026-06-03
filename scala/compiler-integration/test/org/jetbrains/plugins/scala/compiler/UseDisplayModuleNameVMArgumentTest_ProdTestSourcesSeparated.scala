package org.jetbrains.plugins.scala.compiler

import org.jetbrains.plugins.scala.CompilationTests_Zinc
import org.jetbrains.plugins.scala.util.runners.TestJdkVersion
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@Category(Array(classOf[CompilationTests_Zinc]))
@RunWith(classOf[Parameterized])
class UseDisplayModuleNameVMArgumentTest_ProdTestSourcesSeparated(jdkVersion: TestJdkVersion)
  extends DisplayModuleNameTestBase(jdkVersion, separateProdAndTestSources = true) {

  @Test
  def testSingleBuild(): Unit = {
    createSingleBuildProject()
    runTest(true)
  }

  @Test
  def testMultipleBuildsWithUniqueNames(): Unit = {
    createMultipleBuildsProjectWithUniqueNames()
    runTest(true)
  }

  @Test
  def testMultipleBuildsWithDuplicatedNames(): Unit = {
    createMultipleBuildsProjectWithDuplicatedNames()
    runTest(false)
  }
}

private object UseDisplayModuleNameVMArgumentTest_ProdTestSourcesSeparated extends JdkVersionParameters
