package org.jetbrains.plugins.scala.semantic

import org.jetbrains.plugins.scala.corpus.scala3.CatsTest
import org.junit.Test

class CatsSemanticTest extends SemanticTestBase(CatsTest) {
  @Test def align(): Unit = doTest("cats.Align")

  @Test def applicative(): Unit = doTest("cats.Applicative")

  @Test def catsBifoldable(): Unit = doTest("cats.Bifoldable")

  @Test def arrayStack(): Unit = doTest("cats.effect.ArrayStack")

  @Test def callbackStack(): Unit = doTest("cats.effect.CallbackStack")

  @Test def catsEffectIO(): Unit = doTest("cats.effect.IO")

  @Test def catsEffectIOFiber(): Unit = doTest("cats.effect.IOFiber")

  @Test def catsEffectLiftIO(): Unit = doTest("cats.effect.LiftIO")

  @Test def catsEffectSyncIO(): Unit = doTest("cats.effect.SyncIO")

  @Test def catsEffectTrace(): Unit = doTest("cats.effect.Trace")

  @Test def catsKernelEq(): Unit = doTest("cats.kernel.Eq")

  @Test def catsKernelMonoid(): Unit = doTest("cats.kernel.Monoid")

  @Test def catsKernelSemigroup(): Unit = doTest("cats.kernel.Semigroup")
}