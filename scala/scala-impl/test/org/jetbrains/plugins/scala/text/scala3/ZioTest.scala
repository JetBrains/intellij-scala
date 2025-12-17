package org.jetbrains.plugins.scala.text.scala3

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class ZioTest extends TextToTextTestBase(
  dependencies = Seq(
    "dev.zio" %% "zio" % "2.1.23",
    "dev.zio" %% "zio-streams" % "2.1.23",
  ),
  packages = Seq("zio"),
  minClassCount = 266,
  classExceptions = Set(
    "zio.internal.stacktracer.SourceLocation", // Given without a name
  ),
  withSources = true,
  classesWithoutSource = Set(
    "zio.BuildInfo",
    "zio.internal.stacktracer.BuildInfo",
    "zio.stream.BuildInfo",
  ),
  sourceExceptions = Set(
    "zio.Cause", // private | private[Cause]
    "zio.Experimental", // scala.runtime.$throws[A, E] | scala.throws[A, E]
    "zio.Fiber", // case class extends Product & Serializable
    "zio.FiberRef", // private | private[FiberRef]
    "zio.FiberRefs", // @scala.specialized | @scala.specialized(SpecializeInt)
    "zio.HasNoScope", // \n | ' ' (in annotation)
    "zio.HasNoScopeCompanionVersionSpecific", // transparent inline given | final transparent inline given
    "zio.RuntimeFlag", // reference constants
    "zio.Scope", // private type alias
    "zio.ZEnvironment", // izumi.reflect.macrortti
    "zio.ZLayer", // private[Derive]
    "zio.internal.FiberRuntime", // x * y constant
    "zio.internal.LinkedQueue", // Int.MaxValue constant
    "zio.internal.PartitionedRingBuffer", // nQueues * partitionSize constant
    "zio.internal.WeakConcurrentBag", // zio.Duration | DurationModule.this.Duration
    "zio.internal.WeakConcurrentBagGc", // zio.Duration | DurationModule.this.Duration
    "zio.internal.macros.ZLayerDerivationMacros", // Expr[...]
    "zio.internal.macros.LayerMacros", // Expr[...]
    "zio.metrics.MetricPair", // private type alias
    "zio.stm.STM", // zio.BuildFrom vs BuildFromCompat.this.BuildFrom
    "zio.stream.ZChannel", // zio.EnvironmentTag vs VersionSpecific.this.EnvironmentTag
  )
)