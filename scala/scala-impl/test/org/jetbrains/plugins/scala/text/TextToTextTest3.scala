package org.jetbrains.plugins.scala.text

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase.Library

// SCL-21078
class TextToTextTest3 extends TextToTextTestBase {
  override protected def isScala3: Boolean = true

  override protected def libraries: Seq[Library] = Seq(
    Library(
      Seq(
        "com.typesafe.akka" %% "akka-actor" % "2.7.0",
        "com.typesafe.akka" %% "akka-http" % "10.5.0",
        "com.typesafe.akka" %% "akka-stream" % "2.7.0", // Provided dependency of akka-http
      ),
      Seq("akka"), Seq("akka.parboiled2"), 1962,
      Seq(
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
        "akka.http.scaladsl.HttpExt", // No annotation on primary constructor
        "akka.http.scaladsl.server.Directive", // By-name function type parameter
        "akka.http.scaladsl.server.RequestContextImpl", // Extra default arguments
        "akka.http.scaladsl.server.directives.BasicDirectives",
        "akka.http.scaladsl.server.util.BinaryPolyFunc", // Unknown
        "akka.io.TcpListener", // Cannot resolve Matchable
        "akka.io.UdpListener", // Cannot resolve Matchable
        "akka.macros.LogHelperMacro", // Mo inline modifier
        "akka.pattern.BackoffSupervisor", // No annotation on primary constructor
        "akka.stream.ActorMaterializerSettings", // No annotation on primary constructor
        "akka.stream.Supervision", // Excessive parentheses in compound type
        "akka.stream.impl.ConstantFun", // scala.None without .type
        "akka.stream.javadsl.FlowWithContext", // GraphDelegate is Any
        "akka.stream.javadsl.SourceWithContext", // GraphDelegate is Any
        "akka.stream.javadsl.Tcp", // No annotation on primary constructor
        "akka.stream.scaladsl.FlowWithContext", // GraphDelegate is Any
        "akka.stream.scaladsl.MergeHub", // Cannot resolve Event
        "akka.stream.scaladsl.SourceWithContext", // SourceShape is Any
        "akka.stream.scaladsl.Tcp", // No annotation on primary constructor
        "akka.stream.stage.GraphStageLogic", // No annotation on primary constructor
      )
    ),

    Library(
      Seq(
        "org.typelevel" %% "cats-core" % "2.8.0",
        "org.typelevel" %% "cats-effect" % "3.3.14",
      ),
      Seq("cats"), Seq.empty, 1328,
      Seq(
        "cats.ApplicativeMonoid", // ApplySemigroup without qualifier
        "cats.InvariantMonoidalMonoid", // InvariantSemigroupalSemigroup without qualifier
      )
    ),

    Library(
      Seq(
        "co.fs2" %% "fs2-core" % "3.6.1",
      ),
      Seq("fs2"), Seq.empty, 54,
      Seq(
        "fs2.Chunk", // Extra default arguments
        "fs2.ChunkCompanionPlatform", // IArray is Any
        "fs2.ChunkPlatform", // IArray is Any
        "fs2.CollectorPlatform", // type.Aux
        "fs2.Pull", // fs2.Pull.Terminal is Any
        "fs2.Stream", // No prefix in fs2.compat.NotGiven
      )
    ),

    Library(
      Seq(
        "org.scalacheck" %% "scalacheck" % "1.17.0",
      ),
      Seq("org.scalacheck"), Seq.empty, 38,
      Seq.empty
    ),

    Library(
      Seq(
        "org.scalactic" %% "scalactic" % "3.2.14",
      ),
      Seq("org.scalactic"), Seq.empty, 167,
      Seq(
        "org.scalactic.Accumulation", // No parentheses in repeated function type
        "org.scalactic.FutureSugar", // No parentheses in repeated function type
        "org.scalactic.Requirements", // Inline parameter
        "org.scalactic.TrySugar", // No parentheses for repeated function type
        "org.scalactic.anyvals.NumericString", // Inline parameter
        "org.scalactic.anyvals.PercentageInt", // Inline parameter
        "org.scalactic.anyvals.RegexString", // Inline parameter
      )
    ),

    Library(
      Seq(
        "org.scalatest" %% "scalatest" % "3.2.14"
      ),
      Seq("org.scalatest"), Seq.empty, 660,
      Seq(
        "org.scalatest.Assertions", // Extension, inline parameter, anonymous using
        "org.scalatest.CompileMacro", // Given definition
        "org.scalatest.MessageRecordingInformer", // Extra default arguments
        "org.scalatest.Suite", // FromJavaObject
        "org.scalatest.diagrams.Diagrams", // Inline parameter
        "org.scalatest.diagrams.DiagramsMacro", // Cannot resolve reference
        "org.scalatest.enablers.InspectorAsserting", // Tuple2 type argument
        "org.scalatest.matchers.CompileMacro", // Given definition
        "org.scalatest.matchers.Matcher", // Inline parameter
        "org.scalatest.matchers.TypeMatcherMacro", // Cannot resolve reference
        "org.scalatest.matchers.dsl.MatchPatternWord", // Inline parameter
        "org.scalatest.matchers.dsl.MatcherFactory1", // Inline parameter
        "org.scalatest.matchers.dsl.MatcherFactory2", // Inline parameter
        "org.scalatest.matchers.dsl.MatcherFactory3", // Inline parameter
        "org.scalatest.matchers.dsl.MatcherFactory4", // Inline parameter
        "org.scalatest.matchers.dsl.MatcherFactory5", // Inline parameter
        "org.scalatest.matchers.dsl.MatcherFactory6", // Inline parameter
        "org.scalatest.matchers.dsl.MatcherFactory7", // Inline parameter
        "org.scalatest.matchers.dsl.MatcherFactory8", // Inline parameter
        "org.scalatest.matchers.dsl.NotWord", // Inline parameter
        "org.scalatest.matchers.dsl.ResultOfNotWordForAny", // Inline parameter
        "org.scalatest.matchers.must.Matchers", // No this. prefix
        "org.scalatest.matchers.must.TypeMatcherMacro", // Cannot resolve reference
        "org.scalatest.matchers.should.Matchers", // No this. prefix
        "org.scalatest.matchers.should.TypeMatcherMacro", // Cannot resolve reference
//        "org.scalatest.tools.Framework", // Any
        "org.scalatest.tools.Runner", // FromJavaObject
        "org.scalatest.tools.ScalaTestAntTask", // Cannot resolve reference
//        "org.scalatest.tools.ScalaTestFramework", // Any
      )
    ),

    Library(
      Seq(
        "org.scalaz" %% "scalaz-core" % "7.3.7",
        "org.scalaz" %% "scalaz-effect" % "7.3.7",
      ),
      Seq("scalaz"), Seq.empty, 1588,
      Seq(
        "scalaz.Heap", // Excessive parentheses in function type
        "scalaz.\\&/", // id$
        "scalaz.\\/", // id$
      )
    ),

    Library(
      Seq(
        "dev.zio" %% "zio" % "2.0.2",
        "dev.zio" %% "zio-streams" % "2.0.2",
      ),
      Seq("zio"), Seq.empty, 225,
      Seq(
        "zio.Experimental", // Extension
        "zio.ProvideSomePartiallyApplied", // No inline parameter modifier
        "zio.UnsafeVersionSpecific", // Context function type
        "zio.VersionSpecific", // External library reference
        "zio.WirePartiallyApplied", // No inline parameter modifier
        "zio.WireSomePartiallyApplied", // No inline parameter modifier
        "zio.ZIOAppVersionSpecific", // No inline parameter modifier
        "zio.ZIOAppVersionSpecificMacros", // given
        "zio.ZIOCompanionVersionSpecific", // Context function type
        "zio.ZIOVersionSpecific", // No inline parameter modifier
        "zio.json.EncoderLowPriority2", // Type lambda
        "zio.internal.stacktracer.Macros", // External library reference
        "zio.internal.stacktracer.SourceLocation", // Given
        "zio.internal.stacktracer.Tracer", // Given
        "zio.metrics.jvm.BufferPools", // External library reference
        "zio.metrics.jvm.GarbageCollector", // External library reference
        "zio.metrics.jvm.MemoryAllocation", // External library reference
        "zio.metrics.jvm.MemoryPools", // External library reference
        "zio.stream.ZStreamPlatformSpecificConstructors", // .type.Emit
        "zio.stream.ZStreamVersionSpecific", // No inline parameter modifier
      )
    )
  )
}
