package org.jetbrains.plugins.scala.compiler.zinc

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.{ProjectJdkTable, Sdk}
import com.intellij.openapi.roots.{CompilerModuleExtension, ModuleRootManager}
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.platform.externalSystem.testFramework.ExternalSystemImportingTestCase
import com.intellij.testFramework.CompilerTester
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.base.libraryLoaders.SmartJDKLoader
import org.jetbrains.plugins.scala.compiler.CompileServerLauncher
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.settings.ScalaCompileServerSettings
import org.jetbrains.plugins.scala.util.runners.TestJdkVersion
import org.jetbrains.sbt.Sbt
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.jetbrains.sbt.project.{SbtCachesSetupUtil, SbtProjectSystem}
import org.junit.Assert.assertNotNull

import java.io.File
import java.nio.file.Path

abstract class ZincTestBase(separateProdAndTestSources: Boolean = false) extends ExternalSystemImportingTestCase {

  override def getExternalSystemId: ProjectSystemId = SbtProjectSystem.Id

  override def getExternalSystemConfigFileName: String = Sbt.BuildFile

  protected var compiler: CompilerTester = _

  protected var sdk: Sdk = _

  protected var rootModule: Module = _

  override lazy val getCurrentExternalProjectSettings: SbtProjectSettings = {
    val settings = new SbtProjectSettings()
    settings.separateProdAndTestSources = separateProdAndTestSources
    settings.jdk = sdk.getName
    settings
  }

  override def getTestsTempDir: String = this.getClass.getSimpleName

  override def setUp(): Unit = {
    super.setUp()

    sdk = {
      val jdkVersion =
        Option(System.getProperty("filter.test.jdk.version"))
          .map(TestJdkVersion.valueOf)
          .getOrElse(TestJdkVersion.JDK_17)
          .toProductionVersion

      val res = SmartJDKLoader.getOrCreateJDK(jdkVersion)
      val settings = ScalaCompileServerSettings.getInstance()
      settings.COMPILE_SERVER_SDK = res.getName
      settings.USE_DEFAULT_SDK = false
      res
    }

    SbtCachesSetupUtil.setupCoursierAndIvyCache(getProject)
  }

  override def tearDown(): Unit = try {
    CompileServerLauncher.stopServerAndWait()
    compiler.tearDown()
    val settings = ScalaCompileServerSettings.getInstance()
    settings.USE_DEFAULT_SDK = true
    settings.COMPILE_SERVER_SDK = null
    inWriteAction(ProjectJdkTable.getInstance().removeJdk(sdk))
  } finally {
    super.tearDown()
  }

  protected def findClassFileInRootModule(name: String): Path =
    findClassFile(rootModule, name)

  protected def findClassFile(module: Module, name: String): Path = {
    val cls = compiler.findClassFile(name, module)
    assertNotNull(s"Could not find compiled class file $name", cls)
    cls.toPath
  }

  protected def removeFile(path: Path): Unit = {
    val virtualFile = VfsUtil.findFileByIoFile(path.toFile, true)
    inWriteAction {
      virtualFile.delete(null)
    }
  }

  /**
   * It is written as an extension of [[com.intellij.testFramework.CompilerTester#findClassFile]].
   * In this method, if the module in which the className is searched for is a test module,
   * then #getCompilerOutputPathForTests is used instead of #getCompilerOutputPath
   */
  @Nullable
  protected def findClassFile(className: String, module:Module, isTest: Boolean): File = {
    val moduleExtension = ModuleRootManager.getInstance(module).getModuleExtension(classOf[CompilerModuleExtension])
    val out =
      if (isTest) moduleExtension.getCompilerOutputPathForTests
      else moduleExtension.getCompilerOutputPath
    assertNotNull(out)
    val classFile = new File(out.getPath, className.replace('.', '/') + ".class")
    if (classFile.exists()) classFile
    else null
  }

}
