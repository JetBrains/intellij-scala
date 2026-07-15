package org.jetbrains.plugins.scala.semantic

import org.jetbrains.plugins.scala.corpus.scala3.ZioTest
import org.junit.Test

class ZioSemanticTest extends SemanticTestBase(ZioTest) {
  @Test def zioChunk(): Unit = doTest("zio.Chunk")

  @Test def zioClock(): Unit = doTest("zio.Clock")

  @Test def zioConfig(): Unit = doTest("zio.Config")

  @Test def zioConsole(): Unit = doTest("zio.Console")

  @Test def zioFiber(): Unit = doTest("zio.Fiber")

  @Test def zioFiberRef(): Unit = doTest("zio.FiberRef")

  @Test def zioQueue(): Unit = doTest("zio.Queue")

  @Test def zioRuntime(): Unit = doTest("zio.Runtime")

  @Test def zioSemaphore(): Unit = doTest("zio.Semaphore")

  @Test def zioSystem(): Unit = doTest("zio.System")

  @Test def zioZIO(): Unit = doTest("zio.ZIO")

  @Test def zioZIOApp(): Unit = doTest("zio.ZIOApp")

  @Test def zioZLayer(): Unit = doTest("zio.ZLayer")

  @Test def zioZPool(): Unit = doTest("zio.ZPool")
}