package org.jetbrains.sbt.project

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.vfs.VfsUtilCore
import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert.assertEquals
import org.junit.experimental.categories.Category

import java.nio.file.Path

@Category(Array(classOf[SlowTests]))
class UseSeparateCompilerOutputPathsTest extends SbtExternalSystemImportingTestLike {

  override protected def getTestProjectPath: String =
    s"${TestUtils.getTestDataPath}/sbt/projects/separateCompilerOutputPaths"

  override def setUp(): Unit = {
    super.setUp()
    SbtProjectResolver.processOutputOfLatestStructureDump = ""
  }

  private val moduleDirectoryMapping: Map[String, String] = Map(
    "separateCompilerOutputPaths" -> "",
    "module1" -> "module1",
    "module2" -> "module2",
    "module3" -> "module3"
  )

  private def expectedCompilerOutputPath(moduleName: String, scope: String, hasIdeaPrefix: Boolean): Path = {
    val ideaPrefix = if (hasIdeaPrefix) "idea-" else ""
    Path.of(getTestProjectPath)
      .resolve(moduleDirectoryMapping(moduleName))
      .resolve("target")
      .resolve("scala-3.3.1")
      .resolve(s"$ideaPrefix${scope}classes")
  }

  private def doTest(useSeparateCompilerOutputPaths: Boolean): Unit = {
    getCurrentExternalProjectSettings.useSeparateCompilerOutputPaths = useSeparateCompilerOutputPaths
    importProject(false)
    ModuleManager.getInstance(myProject).getModules.filterNot(_.hasBuildModuleType).foreach { module =>
      val extension = CompilerModuleExtension.getInstance(module)
      val compileScopePath = Path.of(VfsUtilCore.urlToPath(extension.getCompilerOutputUrl))
      assertEquals(expectedCompilerOutputPath(module.getName, "", useSeparateCompilerOutputPaths), compileScopePath)
      val testScopePath = Path.of(VfsUtilCore.urlToPath(extension.getCompilerOutputUrlForTests))
      assertEquals(expectedCompilerOutputPath(module.getName, "test-", useSeparateCompilerOutputPaths), testScopePath)
    }
  }

  def testDisabled(): Unit = {
    doTest(useSeparateCompilerOutputPaths = false)
  }

  def testEnabled(): Unit = {
    doTest(useSeparateCompilerOutputPaths = true)
  }
}
