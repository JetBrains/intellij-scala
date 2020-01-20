package org.jetbrains.plugins.scala.compilation

import java.net.{URL, URLClassLoader}

import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.testFramework.CompilerTester
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaSdkOwner
import org.jetbrains.plugins.scala.base.libraryLoaders.LibraryLoader
import org.jetbrains.plugins.scala.compilation.CompilationTestBase.MethodInvocation
import org.jetbrains.plugins.scala.debugger.ScalaCompilerTestBase
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.performance.DownloadingAndImportingTestCase
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.project.{IncrementalityType, LibraryExt, ModuleExt}

import scala.collection.JavaConverters._

/**
 * This test:
 * - downloads project from GitHub
 * - then compiles it
 * - and then runs specified function to ensure that compiled files are correct
 */
abstract class CompilationTestBase
  extends DownloadingAndImportingTestCase
    with ScalaSdkOwner {

  protected def incrementalityType: IncrementalityType = IncrementalityType.IDEA

  protected def allowCompilationWarnings: Boolean = false

  protected def methodInvocation: Option[MethodInvocation]

  override protected def librariesLoaders: Seq[LibraryLoader] = Seq.empty

  private var compiler: CompilerTester = _

  override def setUp(): Unit = {
    super.setUp()
    ScalaCompilerConfiguration.instanceIn(myProject).incrementalityType = incrementalityType
    compiler = new CompilerTester(module)
  }

  override def tearDown(): Unit = try {
    compiler.tearDown()
    ScalaCompilerTestBase.stopAndWait()
    inWriteAction {
      val jdkTable = ProjectJdkTable.getInstance
      jdkTable.getAllJdks.foreach(jdkTable.removeJdk)
    }
  } finally {
    compiler = null
    super.tearDown()
  }

  def testDownloadCompileRun(): Unit = {
    val messages = compiler.rebuild().asScala

    val errors = messages.filter(_.getCategory == CompilerMessageCategory.ERROR)
    assert(errors.isEmpty, s"There are compilation errors: $errors")

    val warnings = messages.filter(_.getCategory == CompilerMessageCategory.WARNING)
    assert(allowCompilationWarnings || warnings.isEmpty, s"There are compilation warnings: $warnings")

    val urls = (librariesUrls ++ targetUrls).toArray
    val classLoader = new URLClassLoader(urls)
    methodInvocation.foreach { case MethodInvocation(className, methodName, args) =>
      val clazz = classLoader.loadClass(className)
      val methodArgTypes = args.map(_.getClass)
      val method = clazz.getMethod(methodName, methodArgTypes: _*)
      method.invoke(null, args: _*)
    }
  }

  private def librariesUrls: Set[URL] =
    module.libraries.flatMap(_.classpathUrls).toSet

  private def targetUrls: Set[URL] =
    ScalaVersion.allScalaVersions.map { version =>
      new URL(s"$projectDir/target/scala-${version.major}/classes/")
    }.toSet

  // TODO is there a better way to do this?
  private def projectDir: String =
    module.getModuleFile.getParent.getParent.getParent.toString

  private def module: Module =
    getModule(githubRepoName)
}

object CompilationTestBase {
  case class MethodInvocation(className: String,
                              methodName: String,
                              args: Array[AnyRef])
}
