package org.jetbrains.plugins.scala.compilation

import java.util.concurrent.TimeUnit

import com.intellij.compiler.CompilerWorkspaceConfiguration
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.CompilerTester
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.jetbrains.plugins.scala.base.ScalaSdkOwner
import org.jetbrains.plugins.scala.base.libraryLoaders.LibraryLoader
import org.jetbrains.plugins.scala.performance.DownloadingAndImportingTestCase
import org.jetbrains.plugins.scala.compilation.CompilerTestUtil.RevertableChange
import org.jetbrains.plugins.scala.debugger.ScalaCompilerTestBase
import org.jetbrains.plugins.scala.debugger.ScalaCompilerTestBase.ListCompilerMessageExt
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.util.Metering._

import scala.collection.JavaConverters.seqAsJavaListConverter

// TODO ignore?
class CompilationBenchmark
  extends DownloadingAndImportingTestCase
    with ScalaSdkOwner {

  // TODO make abstract

  override def githubUsername: String = "lampepfl"

  override def githubRepoName: String = "dotty-example-project"

  override def revision: String = "master"

  override protected def librariesLoaders: Seq[LibraryLoader] = Seq.empty

  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Dotty

  private final val Repeats = 2

  private var revertable: RevertableChange = _
  private var compiler: CompilerTester = _

  override def setUp(): Unit = {
    super.setUp()
    revertable = CompilerTestUtil.withEnabledCompileServer(true)
    revertable.apply()
    compiler = new CompilerTester(myProject, myProject.modules.asJava, null)

    // TODO remove debugging
//    BuildManager.getInstance().setBuildProcessDebuggingEnabled(true)
//    com.intellij.openapi.util.registry.Registry.get("compiler.process.debug.port").setValue(5007)
  }

  override def tearDown(): Unit = try {
    compiler.tearDown()
    ScalaCompilerTestBase.stopAndWait()
    val table = ProjectJdkTable.getInstance
    inWriteAction {
      table.getAllJdks.foreach(table.removeJdk)
    }
  } finally {
    compiler = null
    revertable.revert()
    super.tearDown()
  }

  private implicit val scaleConfig: ScaleConfig = ScaleConfig(1, TimeUnit.SECONDS)

  private def benchmark(params: Params): Unit = {
    val Params(compileInParallel, heapSize, jvmOptions) = params
    CompilerWorkspaceConfiguration.getInstance(myProject).PARALLEL_COMPILATION = compileInParallel
    // TODO apply other parameters
    // TODO restart compile server

    implicit val printReportHandler: MeteringHandler = { (time: Double, unit: TimeUnit) =>
      println(
        s"""==============Benchmark Report==============
            |OS         : ${SystemInfo.OS_NAME} (${SystemInfo.OS_VERSION}, ${SystemInfo.OS_ARCH})
            |Project    : $githubRepoName
            |Parallel   : $compileInParallel
            |Heap Size  : $heapSize
            |JVM Options: $jvmOptions
            |Result     : $time $unit
            |""".stripMargin
      )
    }

    metered {
      for (_ <- Range(0, Repeats))
        compiler.rebuild().assertNoProblems(allowWarnings = true)
    }
  }

  case class Params(compileInParallel: Boolean,
                    heapSize: Int,
                    jvmOptions: String)

  def testBenchmark(): Unit = {
    val paramsList = for {
      compileInParallel <- Seq(true, false)
      heapSize <- Seq(2048, 4096)
      jvmOptions <- Seq(
        "-server -Xss1m"
      )
    } yield Params(
      compileInParallel = compileInParallel,
      heapSize = heapSize,
      jvmOptions = jvmOptions
    )

    for (params <- paramsList)
      benchmark(params)
  }
}
