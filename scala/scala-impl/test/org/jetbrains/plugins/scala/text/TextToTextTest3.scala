package org.jetbrains.plugins.scala.text

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr

// SCL-21078
class TextToTextTest3 extends TextToTextTestBase {
  override protected def isScala3: Boolean = true

  override protected val dependencies = Seq(
    "com.typesafe.akka" %% "akka-actor" % "2.7.0",
    "com.typesafe.akka" %% "akka-http" % "10.5.0",
    "com.typesafe.akka" %% "akka-stream" % "2.7.0", // Provided dependency of akka-http

    "org.typelevel" %% "cats-core" % "2.8.0",
    "org.typelevel" %% "cats-effect" % "3.3.14",

    "co.fs2" %% "fs2-core" % "3.6.1",

    "org.scalaz" %% "scalaz-core" % "7.3.7",
    "org.scalaz" %% "scalaz-effect" % "7.3.7",

    "dev.zio" %% "zio" % "2.0.2",
    "dev.zio" %% "zio-streams" % "2.0.2",
  )

  override protected val packages = Seq(
    "akka",
    "cats",
    "fs2",
//    "scala",
    "scalaz",
    "zio",
  )

  override protected val packageExceptions = Set(
    "akka.stream",
    "akka.parboiled2", // No inline modifier, no anonymous using parameters
  )

  override protected val minClassCount: Int = 4600

  override protected val classExceptions = Set(
    "akka.actor.dungeon.Children", // Any
    "akka.dispatch.CachingConfig", // java.util.Map$.Entry
    "akka.dispatch.Dispatchers", // No annotation on primary constructor
    "akka.dispatch.ExecutorServiceDelegate", // Cannot resolve
    "akka.event.Logging", // .type.type
    "akka.http.impl.engine.parsing.SpecializedHeaderValueParsers", // ContentLengthParser is Any
    "akka.http.impl.engine.rendering.RenderSupport", // .Repr
    "akka.http.impl.model.parser.CacheControlHeader", // Any
    "akka.http.impl.model.parser.CommonRules", // Any
    "akka.http.impl.model.parser.ContentDispositionHeader", // Any
    "akka.http.impl.model.parser.SimpleHeaders", // .Out
    "akka.http.impl.util.JavaMapping", // Cannot resolve S, J
    "akka.http.javadsl.marshalling.Marshaller", // No annotation on type argument
    "akka.http.scaladsl.HttpExt", // No annotation on primary constructor
    "akka.http.scaladsl.server.Directive", // By-name function type parameter
    "akka.http.scaladsl.server.RequestContextImpl", // Extra default arguments
    "akka.http.scaladsl.server.directives.BasicDirectives",
    "akka.http.scaladsl.server.util.BinaryPolyFunc", // Nothing
    "akka.io.TcpListener", // Cannot resolve Matchable
    "akka.io.UdpListener", // Cannot resolve Matchable
    "akka.macros.LogHelperMacro", // Mo inline modifier
    "akka.pattern.BackoffSupervisor", // No annotation on primary constructor

    "cats.ApplicativeMonoid", // ApplySemigroup without qualifier
    "cats.InvariantMonoidalMonoid", // InvariantSemigroupalSemigroup without qualifier
    "cats.effect.IO", // No annotation on type argument
    "cats.effect.IOPlatform", // No annotation on type argument
    "cats.effect.SyncIO", // No annotation on type argument
    "cats.effect.kernel.Resource", // No annotation on type argument

    "fs2.Chunk", // Extra default arguments
    "fs2.ChunkCompanionPlatform", // IArray is Any
    "fs2.ChunkPlatform", // IArray is Any
    "fs2.CollectorPlatform", // type.Aux
    "fs2.Pull", // fs2.Pull.Terminal is Any
    "fs2.Stream", // No prefix in fs2.compat.NotGiven

    "scalaz.Heap", // Excessive parentheses in function type
    "scalaz.\\&/", // id$
    "scalaz.\\/", // id$

    "zio.Experimental", // Extension
    "zio.ProvideSomePartiallyApplied", // No inline parameter modifier
    "zio.TagMacros", // No anonymous using parameter
    "zio.UnsafeVersionSpecific", // Context function type
    "zio.VersionSpecific", // External library reference
    "zio.WirePartiallyApplied", // No inline parameter modifier
    "zio.WireSomePartiallyApplied", // No inline parameter modifier
    "zio.ZIOAppVersionSpecific", // No inline parameter modifier
    "zio.ZIOAppVersionSpecificMacros", // given
    "zio.ZIOCompanionVersionSpecific", // Context function type
    "zio.ZIOVersionSpecific", // No inline parameter modifier
    "zio.internal.macros.LayerMacroUtils", // No anonymous using parameter
    "zio.internal.macros.LayerMacros", // No anonymous using parameter
    "zio.internal.stacktracer.Macros", // External library reference
    "zio.internal.stacktracer.SourceLocation", // Given
    "zio.internal.stacktracer.Tracer", // Given
    "zio.metrics.jvm.BufferPools", // External library reference
    "zio.metrics.jvm.GarbageCollector", // External library reference
    "zio.metrics.jvm.MemoryAllocation", // External library reference
    "zio.metrics.jvm.MemoryPools", // External library reference
    "zio.stream.ZStreamPlatformSpecificConstructors", // .type.Emit
    "zio.stream.ZStreamProvideMacro", // No anonymous using parameter
    "zio.stream.ZStreamVersionSpecific", // No inline parameter modifier
  )
}
