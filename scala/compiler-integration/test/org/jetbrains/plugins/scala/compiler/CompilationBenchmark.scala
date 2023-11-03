package org.jetbrains.plugins.scala.compiler

import com.intellij.compiler.CompilerConfiguration
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.CompilerTester
import org.apache.commons.lang3.exception.ExceptionUtils
import org.jetbrains.jps.incremental.scala.remote.CompileServerMeteringInfo
import org.jetbrains.plugins.scala.base.ScalaSdkOwner
import org.jetbrains.plugins.scala.base.libraryLoaders.LibraryLoader
import org.jetbrains.plugins.scala.compiler.ScalaCompilerTestBase.ListCompilerMessageExt
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.projectHighlighting.base.{GithubRepositoryWithRevision, SbtProjectHighlightingDownloadingFromGithubTestBase}
import org.jetbrains.plugins.scala.settings.ScalaCompileServerSettings
import org.jetbrains.plugins.scala.util.Metering._
import org.jetbrains.plugins.scala.util.{CompilerTestUtil, RevertableChange}
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.junit.Ignore

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

abstract class CompilationBenchmark
  extends SbtProjectHighlightingDownloadingFromGithubTestBase
    with ScalaSdkOwner {

  override protected def librariesLoaders: Seq[LibraryLoader] = Seq.empty

  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_13

  private var revertable: RevertableChange = _
  private var compiler: CompilerTester = _

  override def setUp(): Unit = {
    super.setUp()
    revertable = CompilerTestUtil.withEnabledCompileServer(true)
    revertable.applyChange()
    compiler = new CompilerTester(myProject, myProject.modules.asJava, null, false)
  }

  override def tearDown(): Unit = try {
    compiler.tearDown()
    CompileServerLauncher.stopServerAndWait()
    val table = ProjectJdkTable.getInstance
    inWriteAction {
      table.getAllJdks.foreach(table.removeJdk)
    }
  } finally {
    compiler = null
    revertable.revertChange()
    super.tearDown()
  }

  // All benchmark parameters
  private final val Repeats = 1
  private final val HeapSizeValues = Seq(1024, 2048, 4096)
  private final val CompileInParallelValues = Seq(true)
  private final val PossibleJvmOptions = Seq(
    Seq("-server"),
    Seq("-Xss1m", "-Xss2m", ""),
    Seq("-XX:+UseParallelGC"),
    Seq("-XX:MaxInlineLevel=20", "")
  )

  private def jvmOptionsIterator: Iterator[String] =
    cartesianProduct(PossibleJvmOptions)
      .map(_.filter(_.nonEmpty).sorted.mkString(" "))
      .iterator

  private def cartesianProduct[T](seqs: Seq[Seq[T]]): Seq[Seq[T]] = {
    val initialValue: Seq[Seq[T]] = Seq(Seq.empty)
    seqs.foldLeft(initialValue) { (acc, seq) =>
      acc.flatMap(r => seq.map(x => r ++ Seq(x)))
    }
  }

  private implicit val scaleConfig: ScaleConfig = ScaleConfig(1, TimeUnit.SECONDS)


  private case class Params(compileInParallel: Boolean,
                            heapSize: Int,
                            jvmOptions: String)

  private case class BenchmarkResult(compilationTime: Double,
                                     meteringInfo: CompileServerMeteringInfo)

  private def benchmark(params: Params): Try[BenchmarkResult] = Try {
    val Params(compileInParallel, heapSize, jvmOptions) = params

    CompilerConfiguration.getInstance(myProject).setParallelCompilationEnabled(compileInParallel)
    val settings = ScalaCompileServerSettings.getInstance
    settings.COMPILE_SERVER_MAXIMUM_HEAP_SIZE = heapSize.toString
    settings.COMPILE_SERVER_JVM_PARAMETERS = jvmOptions

    ApplicationManager.getApplication.saveSettings()
    CompileServerLauncher.stopServerAndWait()
    CompileServerLauncher.ensureServerRunning(myProject)

    var resultTime: Double = Double.PositiveInfinity
    implicit val printReportHandler: MeteringHandler = { (time: Double, _) =>
      resultTime = time
    }

    val compileServerClient = CompileServerClient.get(myProject)

    val resultMeteringInfo = compileServerClient.withMetering(FiniteDuration(5, TimeUnit.SECONDS)) {
      benchmarked(Repeats) {
        compiler.rebuild().assertNoProblems(allowWarnings = true)
      }
    }
    BenchmarkResult(resultTime, resultMeteringInfo)
  }

  private def resultAsString(params: Params, result: BenchmarkResult): String = {
    val Params(compileInParallel, heapSize, jvmOptions) = params
    val paramsStr = s"compileInParallel=$compileInParallel;heapSize=$heapSize;jvmOptions=$jvmOptions"

    val timeStr = s"${result.compilationTime} ${scaleConfig.unit}"

    val CompileServerMeteringInfo(maxParallelism, maxHeapSizeMb) = result.meteringInfo
    val meteringStr = s"maxParallelism=$maxParallelism;maxHeapSizeMb=$maxHeapSizeMb"

    s"$paramsStr => $timeStr [$meteringStr]"
  }

  def testBenchmark(): Unit = {
    val paramsList = for {
      compileInParallel <- CompileInParallelValues
      heapSize <- HeapSizeValues
      jvmOptions <- jvmOptionsIterator
    } yield Params(
      compileInParallel = compileInParallel,
      heapSize = heapSize,
      jvmOptions = jvmOptions
    )
    val benchmarksCount = paramsList.size

    println(
      s"""|=====================Info=====================
          |OS:      ${SystemInfo.OS_NAME} (${SystemInfo.OS_VERSION}, ${SystemInfo.OS_ARCH})
          |Project: ${githubRepositoryWithRevision.repositoryUrl}
          |Repeats: $Repeats""".stripMargin
    )

    var results: Map[Params, BenchmarkResult] = Map.empty
    println("==================Progress==================")
    for ((params, i) <- paramsList.zipWithIndex) {
      println(s"Performing benchmark ${i + 1}/$benchmarksCount...")
      val resultMessage = benchmark(params) match {
        case Success(result) =>
          results += params -> result
          resultAsString(params, result)
        case Failure(exception) =>
          ExceptionUtils.getStackTrace(exception)
      }
      println(resultMessage)
    }

    println("==================Result=================")
    results.toSeq.sortBy(_._2.compilationTime).foreach { case (params, time) =>
      println(resultAsString(params, time))
    }
  }
}

@Ignore("Benchmark")
class ZioCompilationBenchmark
  extends CompilationBenchmark {

  override protected def githubRepositoryWithRevision: GithubRepositoryWithRevision =
    GithubRepositoryWithRevision("zio", "zio", "dd21e98ead466bfef5d63e84a77b115122296146")
}

@Ignore("Benchmark")
class DoobieCompilationBenchmark
  extends CompilationBenchmark {

  override protected def githubRepositoryWithRevision: GithubRepositoryWithRevision =
    GithubRepositoryWithRevision("tpolecat", "doobie", "bec7d361a85fa5026e967159615cd1e3d49c09e2")
}