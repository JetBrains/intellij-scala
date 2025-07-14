package org.jetbrains.plugins.scala.debugger.evaluation.sharedSources

import com.intellij.debugger.DebuggerTestCase
import com.intellij.debugger.engine.evaluation.CodeFragmentKind
import com.intellij.debugger.impl.OutputChecker
import com.intellij.execution.configurations.JavaParameters
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiManager
import com.sun.jdi.IntegerValue
import junit.framework.TestCase.assertEquals
import org.jetbrains.plugins.scala.FlakyTests
import org.jetbrains.plugins.scala.compiler.CompileServerTestUtil
import org.jetbrains.plugins.scala.extensions.{PathExt, inReadAction}
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.jetbrains.sbt.project.{SbtCachesSetupUtil, SbtProjectSystem}
import org.junit.experimental.categories.Category

import java.nio.file.Path
import scala.jdk.CollectionConverters.SeqHasAsJava

/**
 * This is an extremely custom-written test. It requires combining two unrelated test base classes, one which allows
 * the debugger to be started in a project and another which imports a project using the ExternalSystem mechanism.
 *
 * I am not confident that this test will not be flaky. If you notice any errors in it, feel free to ignore the test
 * and report the failures to Vasil.
 */
@Category(Array(classOf[FlakyTests]))
class SharedSourcesEvaluationTest extends DebuggerTestCase {

  private var mainModule: Module = _

  override def initOutputChecker(): OutputChecker = new OutputChecker(() => getTestAppPath, () => getTestAppPath) {
    override def checkValid(jdk: Sdk, sortClassPath: Boolean): Unit = {}
  }

  override lazy val getTestAppPath: String = {
    val originalTestDataProjectDir = (TestUtils.getTestDataDir / "debuggerTestData" / "sharedSourcesEvalTest").toCanonicalPath.toFile
    val tempProjectDir = FileUtil.createTempDirectory(s"temp_projects/${originalTestDataProjectDir.getName}", "", false)
    FileUtil.copyDir(originalTestDataProjectDir, tempProjectDir)
    tempProjectDir.getCanonicalPath
  }

  override def setUpProject(): Unit = {
    myProject = doCreateAndOpenProject()

    SbtCachesSetupUtil.setupCoursierAndIvyCache(myProject)
    CompileServerTestUtil.registerLongRunningThreads()

    val settings = new SbtProjectSettings()
    settings.separateProdAndTestSources = true

    ExternalSystemImportingUtil.importProject(
      getProject,
      SbtProjectSystem.Id,
      settings,
      getTestAppPath,
      false
    )

    val modules = ModuleManager.getInstance(getProject).getModules
    mainModule = modules.find(_.getName == "sharedsourcesevaltest.sharedSourcesEvalTest.sharedSourcesEvalTestJVM.main").orNull
  }

  override def getModule: Module = mainModule

  override def getModuleOutputDir: Path = Path.of(getTestAppPath, "jvm", "target", "scala-3.7.1", "classes")

  def testSharedSourcesEval(): Unit = {
    createLocalProcess("SharedMain")

    doWhenPausedThenResume { suspendContext =>
      val x = evaluate(
        CodeFragmentKind.EXPRESSION,
        "NumberHolder().number + List(1, 2, 3).filter(_ % 2 != 0).sum",
        suspendContext
      ).asInstanceOf[IntegerValue].value()
      assertEquals(9, x)
    }
  }

  override protected def createJavaParameters(mainClass: String): JavaParameters = {
    val params = super.createJavaParameters(mainClass)
    params.getClassPath.addAll(mainModule.scalaCompilerClasspath.map(_.toCanonicalPath.toString).asJava)
    params
  }

  override protected def createBreakpoints(className: String): Unit = {
    val path = Path.of(getTestAppPath, "shared", "src", "main", "scala", s"$className.scala")
    val virtualFile = VfsUtil.findFile(path, true)
    val psiFile = inReadAction(PsiManager.getInstance(getProject).findFile(virtualFile))
    super.createBreakpoints(psiFile)
  }
}
