package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.testFramework.CompilerTester
import org.jetbrains.plugins.scala.CompilationTests_Zinc
import org.jetbrains.plugins.scala.compiler.CompilerMessagesUtil.assertNoErrorsOrWarnings
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.sbt.project.SbtExternalSystemImportingTestLike
import org.junit.Assert.{assertNotNull, assertNull, fail}
import org.junit.experimental.categories.Category

import java.nio.file.Path
import scala.jdk.CollectionConverters._

@Category(Array(classOf[CompilationTests_Zinc]))
class SharedSourcesCrossProjectTest_ProdTestSourcesSeparatedEnabled extends SbtExternalSystemImportingTestLike {

  override protected def enableSeparateModulesForProdTest: Boolean = true

  override protected def getTestDataProjectPath: String = s"${TestUtils.getTestDataPath}/../../compiler-integration/testData/sharedSourcesCrossProject"

  override protected def copyTestProjectToTemporaryDir: Boolean = true

  override def setUp(): Unit = {
    super.setUp()

    importProject(false)

    ScalaCompilerConfiguration.instanceIn(myProject).incrementalityType = IncrementalityType.SBT
  }

  override def tearDown(): Unit = {
    CompileServerLauncher.stopServerAndWait()

    super.tearDown()
  }

  def testSharedSourcesOnlyCompiledToOwnerModules(): Unit = {
    val modules = ModuleManager.getInstance(myProject).getModules

    def findModule(name: String): Module = modules.find(_.getName == name).getOrElse {
      fail(s"Could not find module with name '$name").asInstanceOf[Nothing]
    }

    val middleJSMain = findModule("root.middle.middleJS.main")
    val middleJSTest = findModule("root.middle.middleJS.test")
    val middleJVMMain = findModule("root.middle.middleJVM.main")
    val middleJVMTest = findModule("root.middle.middleJVM.test")
    findModule("root.middle.middle-sources.main")

    val baseJSMain = findModule("root.base.baseJS.main")
    val baseJSTest = findModule("root.base.baseJS.test")
    val baseJVMMain = findModule("root.base.baseJVM.main")
    val baseJVMTest = findModule("root.base.baseJVM.test")
    findModule("root.base.base-sources.main")
    findModule("root.base.base-sources.test")

    val compiler = new CompilerTester(myProject, java.util.Arrays.asList(modules: _*), null, false)

    val messages = try {
      compiler.make().asScala.toSeq
    } finally {
      compiler.tearDown()
    }
    assertNoErrorsOrWarnings(messages)

    import SbtProjectCompilationTestBase.findClassFile

    Seq(middleJSMain, middleJVMMain).foreach { module =>
      val sharedClass = findClassFile("Shared", module, isTest = false)
      assertNotNull(s"Shared class file not found in ${module.getName}", sharedClass)
    }

    def fileIsNullAssertion(sharedClass: Path, moduleName: String): Unit =
      assertNull(s"Shared class file found in $moduleName, but it shouldn't", sharedClass)

    Seq(baseJSMain,baseJVMMain).foreach { module =>
      val sharedClass = findClassFile("Shared", module, isTest = false)
      fileIsNullAssertion(sharedClass, module.getName)
    }

    Seq(baseJSTest, middleJSTest, middleJVMTest, baseJVMTest).foreach { module =>
      val sharedClass = findClassFile("Shared", module, isTest = true)
      fileIsNullAssertion(sharedClass, module.getName)
    }
  }
}
