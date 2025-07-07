package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.{ProjectJdkTable, Sdk}
import com.intellij.openapi.roots.{CompilerModuleExtension, ModuleRootManager}
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.platform.externalSystem.testFramework.ExternalSystemImportingTestCase
import com.intellij.testFramework.CompilerTester
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.base.libraryLoaders.SmartJDKLoader
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.settings.ScalaCompileServerSettings
import org.jetbrains.sbt.Sbt
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.jetbrains.sbt.project.{SbtCachesSetupUtil, SbtProjectSystem}
import org.junit.Assert.assertNotNull

import java.nio.file.{Files, Path}

/**
 * A base class for writing tests which:
 *   1. define an sbt project programmatically
 *   1. import the project using the external system machinery
 *   1. compile it using the JSP IDEA build system
 *   1. do assertions on the produced compiler messages and output class files.
 *
 * IMPORTANT:<br>
 * Avoid generating test projects programmatically in this test base class when the project requires more than 2 files.
 * Programmatically generated projects make it difficult to:
 * - Analyze and understand the project structure
 * - Import the test data into Dev IntelliJ instance
 * - Maintain and modify tests
 *
 * @see [[com.intellij.platform.externalSystem.testFramework.ExternalSystemTestCase]] for methods used for defining
 *      a project programmatically, or look at other test classes which extend [[SbtProjectCompilationTestBase]]
 *      for examples.
 */
abstract class SbtProjectCompilationTestBase(separateProdAndTestSources: Boolean = false) extends ExternalSystemImportingTestCase {

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

  protected def reuseCompileServerProcessBetweenTests: Boolean = true

  override def setUp(): Unit = {
    super.setUp()

    sdk = {
      val jdkVersion = JdkVersionDiscovery.discoveredJdk
      val res = SmartJDKLoader.getOrCreateJDK(jdkVersion)
      val settings = ScalaCompileServerSettings.getInstance()
      settings.COMPILE_SERVER_SDK = res.getName
      settings.USE_DEFAULT_SDK = false
      res
    }

    SbtCachesSetupUtil.setupCoursierAndIvyCache(getProject)

    if (reuseCompileServerProcessBetweenTests) {
      CompileServerTestUtil.registerLongRunningThreads()
    } else {
      // We don't want to reuse the compile server in this test class, but it may have already been started.
      // We should shut it down first.
      CompileServerLauncher.stopServerAndWait()
    }
  }

  override def tearDown(): Unit = try {
    if (!reuseCompileServerProcessBetweenTests) {
      CompileServerLauncher.stopServerAndWait()
    }
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
    val virtualFile = VfsUtil.findFile(path, true)
    inWriteAction {
      virtualFile.delete(null)
    }
  }

  protected final def findClassFile(className: String, module:Module, isTest: Boolean): Path =
    SbtProjectCompilationTestBase.findClassFile(className, module, isTest)
}

object SbtProjectCompilationTestBase {


  /**
   * It is written as an extension of [[com.intellij.testFramework.CompilerTester#findClassFile]].
   * In this method, if the module in which the className is searched for is a test module,
   * then #getCompilerOutputPathForTests is used instead of #getCompilerOutputPath
   */
  @Nullable
  def findClassFile(className: String, module:Module, isTest: Boolean): Path = {
    val moduleExtension = ModuleRootManager.getInstance(module).getModuleExtension(classOf[CompilerModuleExtension])
    val out =
      if (isTest) moduleExtension.getCompilerOutputPathForTests
      else moduleExtension.getCompilerOutputPath
    assertNotNull(out)
    val classFile = out.toNioPath.resolve(className.replace('.', '/') ++ ".class")
    if (Files.exists(classFile)) classFile
    else null
  }
}
