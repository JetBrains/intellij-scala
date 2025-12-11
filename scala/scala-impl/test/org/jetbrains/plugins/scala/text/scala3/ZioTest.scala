package org.jetbrains.plugins.scala.text.scala3

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class ZioTest extends TextToTextTestBase(
  dependencies = Seq(
    "dev.zio" %% "zio" % "2.0.22",
    "dev.zio" %% "zio-streams" % "2.0.22",
  ),
  packages = Seq("zio"),
  minClassCount = 225,
  classExceptions = Set(
    "zio.Experimental", // Cannot resolve scala.runtime.$throws
    "zio.internal.stacktracer.SourceLocation", // Given without a name
  ),
  withSources = true,
  sourceExceptions = Set(
    "zio.Fiber", // case class extends Product & Serializable
    "zio.RuntimeFlag", // reference constants
    "zio.Scope", // private type alias
    "zio.ZEnvironment", // izumi.reflect.macrortti
    "zio.ZLayer", // private[Derive]
    "zio.ZLogger", // izumi.reflect.macrortti
    "zio.internal.FiberRuntime", // x * y constant
    "zio.internal.LinkedQueue", // Int.MaxValue constant
    "zio.internal.macros.ZLayerDerivationMacros", // Expr[...]
    "zio.metrics.MetricPair", // private type alias
    "zio.stm.STM", // zio.BuildFrom vs BuildFromCompat.this.BuildFrom
    "zio.stm.ZSTM", // protected vs private[this]
    "zio.stream.ZChannel", // zio.EnvironmentTag vs VersionSpecific.this.EnvironmentTag
  )
)