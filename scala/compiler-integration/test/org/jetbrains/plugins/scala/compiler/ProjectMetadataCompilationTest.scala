package org.jetbrains.plugins.scala.compiler

import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.util.JDOMUtil
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.CompilerTester
import org.jetbrains.jps.incremental.scala.{ScalaJpsProjectMetadata, ScalaJpsProjectMetadataConstants}
import org.jetbrains.plugins.scala.CompilationTests_Zinc
import org.jetbrains.plugins.scala.compiler.CompilerMessagesUtil.assertNoErrorsOrWarnings
import org.jetbrains.plugins.scala.compiler.testUtils.CompileServerTestUtil
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.sbt.project.ScalaExternalSystemImportingTestBase.TestProjectCopyOptions
import org.jetbrains.sbt.project.SbtExternalSystemImportingTestLike
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import scala.jdk.CollectionConverters.CollectionHasAsScala

@RunWith(classOf[JUnit4])
@Category(Array(classOf[CompilationTests_Zinc]))
class ProjectMetadataCompilationTest extends SbtExternalSystemImportingTestLike {

  override protected def getTestDataProjectPath: String =
    s"${TestUtils.getTestDataPath}/../../compiler-integration/testData/projectMetadata"

  override protected def getTestProjectCopyOptions: TestProjectCopyOptions =
    super.getTestProjectCopyOptions.copy(copyToTemporaryDir = true)

  override protected def projectJdkLanguageLevel: LanguageLevel = LanguageLevel.JDK_21

  override def setUp(): Unit = {
    super.setUp()
    CompileServerTestUtil.registerLongRunningThreads()
  }

  @Test
  def checkProjectMetadata(): Unit = {
    importProject(false)

    val project = getMyProject
    val modules = ModuleManager.getInstance(project).getModules

    val compiler = new CompilerTester(project, java.util.Arrays.asList(modules*), null, false)
    val messages =
      try compiler.make().asScala.toSeq
      finally compiler.tearDown()

    assertNoErrorsOrWarnings(messages)

    import ScalaJpsProjectMetadataConstants._

    val projectSystemDirectory = BuildManager.getInstance().getProjectSystemDir(project)
    val filePath = projectSystemDirectory / ScalaJpsProjectMetadataFileName
    val crcFilePath = projectSystemDirectory / ScalaJpsProjectMetadataCrcFileName

    assertTrue(s"Project metadata file not found at $filePath", filePath.exists)
    assertTrue(s"Project metadata crc file not found at $crcFilePath", crcFilePath.exists)

    val xml = JDOMUtil.load(filePath)
    val actualProjectMetadata = ScalaJpsProjectMetadata.parseXml(xml)

    val expectedProjectMetadata = ProjectMetadataUtil.jpsProjectMetadata(project)
    assertEquals(Some(expectedProjectMetadata), actualProjectMetadata)
  }
}
