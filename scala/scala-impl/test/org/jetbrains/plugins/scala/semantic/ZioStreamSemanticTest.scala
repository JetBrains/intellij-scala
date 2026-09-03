package org.jetbrains.plugins.scala.semantic

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.semantic.SemanticTestBase.scalaVersion
import org.junit.Test

class ZioStreamSemanticTest extends SemanticTestBase("dev.zio" %% "zio-streams" % "2.1.23")("zio.stream") {
//  @Test def single(): Unit = doTest("")

  @Test def test(): Unit = doTest("""
    //zio.stream.BuildInfo
    //zio.stream.Deflate
    //zio.stream.Gunzip
    //zio.stream.Gzip
    //zio.stream.Inflate
    //zio.stream.SubscriptionRef
    //zio.stream.Take
    //zio.stream.ZChannel
    //zio.stream.ZPipeline
    //zio.stream.ZPipelinePlatformSpecificConstructors
    //zio.stream.ZSink
    //zio.stream.ZSinkPlatformSpecificConstructors
    //zio.stream.ZStream
    //zio.stream.ZStreamAspect
    //zio.stream.ZStreamPlatformSpecificConstructors
    //zio.stream.ZStreamProvideMacro
    //zio.stream.ZStreamVersionSpecific
    zio.stream.compression.CompressionException
    zio.stream.compression.CompressionLevel
    zio.stream.compression.CompressionParameters
    zio.stream.compression.CompressionStrategy
    //zio.stream.compression.Deflate
    zio.stream.compression.FlushMode
    //zio.stream.compression.Gunzipper
    //zio.stream.compression.Gzipper
    zio.stream.encoding.EncodingException
    zio.stream.internal.AsyncInputConsumer
    zio.stream.internal.AsyncInputProducer
    //zio.stream.internal.ChannelExecutor
    zio.stream.internal.CharacterSet
    //zio.stream.internal.SingleProducerAsyncInput
    //zio.stream.internal.ZInputStream
    //zio.stream.internal.ZReader
  """)
}