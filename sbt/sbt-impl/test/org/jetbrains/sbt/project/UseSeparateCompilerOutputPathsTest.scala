package org.jetbrains.sbt.project

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.module.Module
import org.jetbrains.plugins.scala.SlowTests2
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.sbt.SbtSourceSetUtil.SbtSourceSetModuleExt
import org.junit.Assert.assertEquals
import org.junit.experimental.categories.Category

import java.nio.file.Path

@Category(Array(classOf[SlowTests2]))
class UseSeparateCompilerOutputPathsTest extends SbtExternalSystemImportingTestLike {

  override protected def getTestDataProjectPath: String =
    s"${TestUtils.getTestDataPath}/sbt/projects/separateCompilerOutputPaths"

  private val moduleDirectoryMapping: Map[String, String] = Map(
    "separateCompilerOutputPaths.main" -> "",
    "separateCompilerOutputPaths.test" -> "",
    "separateCompilerOutputPaths.module1.main" -> "module1",
    "separateCompilerOutputPaths.module1.test" -> "module1",
    "separateCompilerOutputPaths.module2.main" -> "module2",
    "separateCompilerOutputPaths.module2.test" -> "module2",
    "separateCompilerOutputPaths.module3.main" -> "module3",
    "separateCompilerOutputPaths.module3.test" -> "module3"
  )

  private def expectedCompilerOutputPath(moduleName: String, scope: String, hasIdeaPrefix: Boolean): Path = {
    val ideaPrefix = if (hasIdeaPrefix) "idea-" else ""
    Path.of(getTestDataProjectPath)
      .resolve(moduleDirectoryMapping(moduleName))
      .resolve("target")
      .resolve("scala-3.3.1")
      .resolve(s"$ideaPrefix${scope}classes")
  }

  private def doTest(useSeparateCompilerOutputPaths: Boolean): Unit = {
    getCurrentExternalProjectSettings.useSeparateCompilerOutputPaths = useSeparateCompilerOutputPaths
    importProject(false)
    val (mainModules, testModules) = ModuleManager.getInstance(getMyProject).getModules.filter(_.isSbtSourceSetModule).partition(_.isMain)
    val modulesCount = mainModules.length + testModules.length
    assertEquals(s"There should be 8 main/test modules, but there are $modulesCount", 8, modulesCount)

    def getCompilerOutput(module: Module, isMain: Boolean): Path = {
      val extension = CompilerModuleExtension.getInstance(module)
      val outputUrl =
        if (isMain) extension.getCompilerOutputUrl
        else extension.getCompilerOutputUrlForTests

      Path.of(VfsUtilCore.urlToPath(outputUrl))
    }

    mainModules.foreach { module =>
      val compilerOutput = getCompilerOutput(module, isMain = true)
      assertEquals(expectedCompilerOutputPath(module.getName, "", useSeparateCompilerOutputPaths), compilerOutput)
    }
    testModules.foreach { module =>
      val compilerOutput = getCompilerOutput(module, isMain = false)
      assertEquals(expectedCompilerOutputPath(module.getName, "test-", useSeparateCompilerOutputPaths), compilerOutput)
    }
  }

  def testDisabled(): Unit = {
    doTest(useSeparateCompilerOutputPaths = false)
  }

  def testEnabled(): Unit = {
    doTest(useSeparateCompilerOutputPaths = true)
  }
}
