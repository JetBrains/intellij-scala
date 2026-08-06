package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.module.ModuleManager
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.CompilerTester
import org.jetbrains.plugins.scala.compiler.CompilerMessagesUtil.assertNoErrorsOrWarnings
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.compiler.testUtils.CompileServerTestUtil
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.{CompilationTests_IDEA, CompilationTests_Zinc}
import org.jetbrains.sbt.project.SbtExternalSystemImportingTestLike
import org.jetbrains.sbt.project.ScalaExternalSystemImportingTestBase.TestProjectCopyOptions
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import scala.jdk.CollectionConverters.CollectionHasAsScala

@RunWith(classOf[JUnit4])
class NestedSourceDirectoriesDeduplicationTest extends SbtExternalSystemImportingTestLike {

  override protected def getTestDataProjectPath: String =
    s"${TestUtils.getTestDataPath}/../../compiler-integration/testData/nestedSourceDirectoriesDeduplication"

  override protected def getTestProjectCopyOptions: TestProjectCopyOptions =
    super.getTestProjectCopyOptions.copy(copyToTemporaryDir = true)

  override protected def projectJdkLanguageLevel: LanguageLevel = LanguageLevel.JDK_21

  override def setUp(): Unit = {
    super.setUp()
    CompileServerTestUtil.registerLongRunningThreads()
  }

  @Test
  @Category(Array(classOf[CompilationTests_Zinc]))
  def nestedSourceDirectoriesDeduplication_Zinc(): Unit = {
    runNestedSourceDirectoriesDeduplicationTest(IncrementalityType.SBT)
  }

  @Test
  @Category(Array(classOf[CompilationTests_IDEA]))
  def nestedSourceDirectoriesDeduplication_IDEA(): Unit = {
    runNestedSourceDirectoriesDeduplicationTest(IncrementalityType.IDEA)
  }

  private def runNestedSourceDirectoriesDeduplicationTest(incrementality: IncrementalityType): Unit = {
    importProject(false)

    val project = getMyProject
    ScalaCompilerConfiguration.instanceIn(project).incrementalityType = incrementality

    val modules = ModuleManager.getInstance(project).getModules

    val compiler = new CompilerTester(project, java.util.Arrays.asList(modules*), null, false)
    val messages =
      try compiler.make().asScala.toSeq
      finally compiler.tearDown()

    assertNoErrorsOrWarnings(messages)
  }
}
