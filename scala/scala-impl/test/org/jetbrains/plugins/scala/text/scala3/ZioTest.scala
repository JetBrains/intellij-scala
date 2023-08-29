package org.jetbrains.plugins.scala.text.scala3

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class ZioTest extends TextToTextTestBase(
  Seq(
    "dev.zio" %% "zio" % "2.0.2",
    "dev.zio" %% "zio-streams" % "2.0.2",
  ),
  Seq("zio"), Set.empty, 225,
  Set(
    "zio.Experimental", // Cannot resolve scala.runtime.$throws
    "zio.ProvideSomePartiallyApplied", // No inline parameter modifier
    "zio.WirePartiallyApplied", // No inline parameter modifier
    "zio.WireSomePartiallyApplied", // No inline parameter modifier
    "zio.ZIOAppVersionSpecific", // No inline parameter modifier
    "zio.ZIOVersionSpecific", // No inline parameter modifier
    "zio.json.EncoderLowPriority2", // Type lambda
    "zio.internal.stacktracer.Macros", // External library reference
    "zio.internal.stacktracer.SourceLocation", // Given without a name
    "zio.metrics.jvm.BufferPools", // External library reference
    "zio.metrics.jvm.GarbageCollector", // External library reference
    "zio.metrics.jvm.MemoryAllocation", // External library reference
    "zio.metrics.jvm.MemoryPools", // External library reference
    "zio.stream.ZStreamPlatformSpecificConstructors", // .type.Emit
    "zio.stream.ZStreamVersionSpecific", // No inline parameter modifier
  )
)