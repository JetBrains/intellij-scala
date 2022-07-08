package org.jetbrains.plugins.scala.lang.benchmarks.conformance

import java.util.concurrent.TimeUnit

import com.intellij.psi.PsiManager
import com.intellij.psi.impl.PsiModificationTrackerImpl
import org.jetbrains.plugins.scala.lang.benchmarks._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt}
import org.jetbrains.plugins.scala.lang.typeConformance.TypeConformanceTestBase
import org.openjdk.jmh.annotations.{Measurement, OutputTimeUnit, Warmup, _}
import org.openjdk.jmh.infra.Blackhole

@BenchmarkMode(Array(Mode.AverageTime))
@Fork(1)
@State(Scope.Benchmark)
abstract class TypeConformanceBenchmarkBase(testName: String) extends TypeConformanceTestBase {
  var lType: ScType = _
  var rType: ScType = _
  var scalaPsiManager: ScalaPsiManager = _
  var psiModTracker: PsiModificationTrackerImpl = _

  override def folderPath: String = "testdata/conformance/"

  def fileName = testName + ".scala"

  override def getName = s"test$testName"

  def setupData(): Unit = {
    configureFromFile(fileName)
    val (_lType, _rType) = declaredAndExpressionTypes()
    lType = _lType
    rType = _rType
    scalaPsiManager = ScalaPsiManager.instance(getProjectAdapter)
    psiModTracker = PsiManager.getInstance(getProjectAdapter).getModificationTracker.asInstanceOf[PsiModificationTrackerImpl]
  }

  @Setup(Level.Trial)
  def trialSetup(): Unit = syncInEdt {
    setUp()
    setupData()
  }

  @TearDown(Level.Trial)
  def trialTearDown(): Unit = syncInEdt {
    tearDown()
    scheduleShutdown(200L)
  }

  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  @Warmup(iterations = 3, time = 10, timeUnit = TimeUnit.SECONDS)
  @Measurement(iterations = 10, time = 3, timeUnit = TimeUnit.SECONDS)
  @Benchmark
  def conformsUncached(bh: Blackhole): Unit = syncInEdt {
    val result = rType.conforms(lType)
    bh.consume(result)
    scalaPsiManager.clearAllCaches()
    psiModTracker.incOutOfCodeBlockModificationCounter()
  }

  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  @Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
  @Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
  @Benchmark
  def conformsCachedJavaStructure(bh: Blackhole): Unit = syncInEdt {
    val result = rType.conforms(lType)
    bh.consume(result)
    scalaPsiManager.clearCachesOnChange()
  }
}
