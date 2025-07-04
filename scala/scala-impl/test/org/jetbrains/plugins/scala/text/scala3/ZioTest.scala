package org.jetbrains.plugins.scala.text.scala3

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class ZioTest extends TextToTextTestBase(
  Seq(
    "dev.zio" %% "zio" % "2.0.22",
    "dev.zio" %% "zio-streams" % "2.0.22",
  ),
  Seq("zio"), Set.empty, 225,
  Set(
    "zio.Experimental", // Cannot resolve scala.runtime.$throws
    "zio.internal.stacktracer.SourceLocation", // Given without a name
    "zio.metrics.jvm.BufferPools", // External library reference
    "zio.metrics.jvm.GarbageCollector", // External library reference
    "zio.metrics.jvm.MemoryAllocation", // External library reference
    "zio.metrics.jvm.MemoryPools", // External library reference
    "zio.stream.ZStreamPlatformSpecificConstructors", // .type.Emit
  ),
  withSources = true,
  Set(
    "zio.Fiber", // case class extends Product & Serializable
    "zio.RuntimeFlag", // reference constants
    "zio.Scope", // private type alias
    "zio.ZEnvironment", // izumi.reflect.macrortti
    "zio.ZLayer", // private[Derive]
    "zio.ZLogger", // izumi.reflect.macrortti
    "zio.internal.FiberRuntime", // x * y constant
    "zio.internal.LinkedQueue", // Int.MaxValue constant
    "zio.internal.macros.LayerBuilder", // scala.List
    "zio.internal.macros.ZLayerDerivationMacros", // Expr[...]
    "zio.metrics.MetricPair", // private type alias
    "zio.stm.STM", // zio.BuildFrom vs BuildFromCompat.this.BuildFrom
    "zio.stm.ZSTM", // protected vs private[this]
    "zio.stream.Take", // no final for case class
    "zio.stream.ZChannel", // zio.EnvironmentTag vs VersionSpecific.this.EnvironmentTag
  )
)