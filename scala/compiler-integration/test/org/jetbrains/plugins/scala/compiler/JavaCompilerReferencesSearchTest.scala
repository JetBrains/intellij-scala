package org.jetbrains.plugins.scala.compiler

import com.intellij.compiler.{CompilerDirectHierarchyInfo, CompilerReferenceService}
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.projectRoots.{ProjectJdkTable, Sdk}
import com.intellij.openapi.vfs.{VfsUtil, VirtualFileUtil}
import com.intellij.platform.externalSystem.testFramework.ExternalSystemImportingTestCase
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.CompilerTester
import org.jetbrains.plugins.scala.CompilationTests
import org.jetbrains.plugins.scala.base.libraryLoaders.SmartJDKLoader
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.settings.ScalaCompileServerSettings
import org.jetbrains.plugins.scala.util.runners.TestJdkVersion
import org.jetbrains.sbt.Sbt
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.junit.Assert.{assertEquals, assertNotNull, assertNull, assertTrue}
import org.junit.experimental.categories.Category

import java.nio.file.Path
import scala.jdk.CollectionConverters._

@Category(Array(classOf[CompilationTests]))
abstract class JavaCompilerReferencesSearchTestBase(incrementality: IncrementalityType) extends ExternalSystemImportingTestCase {

  private var sdk: Sdk = _

  private var compiler: CompilerTester = _

  override lazy val getCurrentExternalProjectSettings: SbtProjectSettings = {
    val settings = new SbtProjectSettings()
    settings.jdk = sdk.getName
    settings
  }

  override def getExternalSystemId: ProjectSystemId = SbtProjectSystem.Id

  override def getTestsTempDir: String = this.getClass.getSimpleName

  override def getExternalSystemConfigFileName: String = Sbt.BuildFile

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

    createProjectSubDirs("project", "src/main/java")
    createProjectSubFile("project/build.properties",
      """sbt.version=1.9.7
        |""".stripMargin)
    createProjectSubFile("src/main/java/Greeter.java",
      """public interface Greeter {
        |  String greeting();
        |}
        |""".stripMargin)
    createProjectSubFile("src/main/java/FooGreeter.java",
      """public class FooGreeter implements Greeter {
        |  @Override
        |  public String greeting() {
        |    return "Foo";
        |  }
        |}
        |""".stripMargin)
    createProjectSubFile("src/main/java/BarGreeter.java",
      """public class BarGreeter implements Greeter {
        |  @Override
        |  public String greeting() {
        |    return "Bar";
        |  }
        |}
        |""".stripMargin)
    createProjectConfig(
      """lazy val root = project.in(file("."))
        |  .settings(scalaVersion := "2.13.12")
        |""".stripMargin)
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

  def testImportAndCompile(): Unit = {
    importProject(false)
    val compilerReferenceService = CompilerReferenceService.getInstance(myProject)

    ScalaCompilerConfiguration.instanceIn(myProject).incrementalityType = incrementality

    val modules = ModuleManager.getInstance(myProject).getModules
    compiler = new CompilerTester(myProject, java.util.Arrays.asList(modules: _*), null, false)

    val messages = compiler.make()
    val errorsAndWarnings = messages.asScala.filter { message =>
      val category = message.getCategory
      category == CompilerMessageCategory.ERROR || category == CompilerMessageCategory.WARNING
    }

    assertTrue(
      s"Expected no compilation errors or warnings, got: ${errorsAndWarnings.mkString(System.lineSeparator())}",
      errorsAndWarnings.isEmpty
    )

    val rootModule = modules.find(_.getName == "root").orNull
    assertNotNull("Could not find module with name 'root'", rootModule)

    val projectPath = Path.of(getProjectPath)
    val greeterPsiClass =
      Option(VfsUtil.findFile(projectPath.resolve("src/main/java/Greeter.java"), true))
        .flatMap(source => Option(VirtualFileUtil.findPsiFile(source, myProject)))
        .flatMap(psiFile => Option(PsiTreeUtil.findChildOfType(psiFile, classOf[PsiClass])))
        .orNull

    assertNotNull("Could not find the 'Greeter' PsiClass", greeterPsiClass)

    val info = compilerReferenceService.getDirectInheritors(
      greeterPsiClass,
      greeterPsiClass.getUseScope.asInstanceOf[GlobalSearchScope],
      JavaFileType.INSTANCE
    )

    assertCompilerDirectHierarchyInfo(info)
  }

  protected def assertCompilerDirectHierarchyInfo(info: CompilerDirectHierarchyInfo): Unit
}

class JavaCompilerReferencesSearchTest_IDEA extends JavaCompilerReferencesSearchTestBase(IncrementalityType.IDEA) {
  override protected def assertCompilerDirectHierarchyInfo(info: CompilerDirectHierarchyInfo): Unit = {
    val references = info.getHierarchyChildren.toList
    assertEquals(2, references.size())
  }
}

class JavaCompilerReferencesSearchTest_Zinc extends JavaCompilerReferencesSearchTestBase(IncrementalityType.SBT) {
  override protected def assertCompilerDirectHierarchyInfo(info: CompilerDirectHierarchyInfo): Unit = {
    // The hierarchy info returned by the compiler reference service should be null when the indices are disabled for
    // a project.
    assertNull(info)
    // At the moment, because we cannot generate Java compiler references using Zinc, we fall back to PSI search.
    // In the future, when SCL-21719 is implemented, this test is supposed to fail and be rewritten with different
    // expectations, similar (or identical) to the IDEA test above.
  }
}
