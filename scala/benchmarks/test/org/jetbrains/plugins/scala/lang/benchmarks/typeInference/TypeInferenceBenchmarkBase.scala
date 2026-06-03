package org.jetbrains.plugins.scala.lang.benchmarks.typeInference

import java.util.concurrent.TimeUnit

import com.intellij.psi.PsiManager
import com.intellij.psi.impl.PsiModificationTrackerImpl
import org.jetbrains.plugins.scala.lang.benchmarks._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

@BenchmarkMode(Array(Mode.AverageTime))
@Fork(1)
@State(Scope.Benchmark)
abstract class TypeInferenceBenchmarkBase(testName: String) extends TypeInferenceTestBase {
  var expr: ScExpression = _
  var scalaPsiManager: ScalaPsiManager = _
  var psiModTracker: PsiModificationTrackerImpl = _

  override protected def folderPath: String = "testdata/typeInference/"

  def fileName = testName + ".scala"

  override def getName = s"test$testName"

  def setupData(): Unit = {
    val file = configureFromFileText(fileName, None)
    expr = findExpression(file)
    scalaPsiManager = ScalaPsiManager.instance(getProjectAdapter)
    psiModTracker = PsiManager.getInstance(getProjectAdapter).getModificationTracker.asInstanceOf[PsiModificationTrackerImpl]
  }

  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  @Warmup(iterations = 3, time = 10, timeUnit = TimeUnit.SECONDS)
  @Measurement(iterations = 10, time = 3, timeUnit = TimeUnit.SECONDS)
  @Benchmark
  def exprTypeUncached(bh: Blackhole): Unit = syncInEdt {
    bh.consume(expr.`type`())
    scalaPsiManager.clearAllCaches()
    psiModTracker.incCounter()
  }

  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  @Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
  @Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
  @Benchmark
  def exprTypeCachedJavaStructure(bh: Blackhole): Unit = syncInEdt {
    bh.consume(expr.`type`())
    scalaPsiManager.clearCachesOnChange()
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
}
