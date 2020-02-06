package org.jetbrains.plugins.scala.compilerReferences

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.zip.{DeflaterOutputStream, InflaterInputStream}

import org.jetbrains.jps.incremental.messages.CustomBuilderMessage
import Builder.id
import org.jetbrains.plugins.scala.indices.protocol.IdeaIndicesJsonProtocol._
import org.jetbrains.plugins.scala.indices.protocol.jps.JpsCompilationInfo
import spray.json._

import scala.util.Try

object Messages {
  val compilationDataType     = "compilation-data"
  val compilationFinishedType = "compilation-finished"
  val compilationStartedType  = "compilation-started"

  final case class ChunkCompilationInfo(data: JpsCompilationInfo)
    extends CustomBuilderMessage(id, compilationDataType, compressCompilationInfo(data))

  final case object CompilationFinished
    extends CustomBuilderMessage(id, compilationFinishedType, "")

  final case class CompilationStarted(isCleanBuild: Boolean)
    extends CustomBuilderMessage(id, compilationStartedType, isCleanBuild.toString)

  def compressCompilationInfo(data: JpsCompilationInfo): String = {
    val baos     = new ByteArrayOutputStream(8191)
    val json     = data.toJson.compactPrint

    tryWith(new DeflaterOutputStream(baos))(
      _.write(json.getBytes(StandardCharsets.UTF_8))
    )

    Base64.getEncoder.encodeToString(baos.toByteArray)
  }

  def decompressCompilationInfo(b64encoded: String): Try[JpsCompilationInfo] = {
    val decoded = Base64.getDecoder.decode(b64encoded)
    val bais    = new ByteArrayInputStream(decoded)

    val inflated =
      tryWith(new InflaterInputStream(bais)) { inflater =>
        val out    = new ByteArrayOutputStream
        val buffer = new Array[Byte](8192)
        var read   = 0

        while ({ read = inflater.read(buffer); read > 0 }) {
          out.write(buffer, 0, read)
        }
        out.close()
        out.toByteArray
      }

    for {
      bytes   <- inflated
      json    = new String(bytes, StandardCharsets.UTF_8)
      jpsInfo <- Try(json.parseJson.convertTo[JpsCompilationInfo])
    } yield jpsInfo
  }

  private[this] def tryWith[R <: AutoCloseable, T](resource: => R)(f: R => T): Try[T] =
    Try(resource).flatMap { resource =>
      Try(f(resource)).flatMap { result =>
        Try {
          if (resource != null) resource.close()
        }.map(_ => result)
      }
    }
}
