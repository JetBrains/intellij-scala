package org.jetbrains.plugins.scala.compilerReferences

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.nio.charset.StandardCharsets
import java.util.zip.{DeflaterOutputStream, InflaterInputStream}
import org.jetbrains.jps.incremental.messages.CustomBuilderMessage
import Builder.id
import org.jetbrains.plugins.scala.indices.protocol.IdeaIndicesJsonProtocol._
import org.jetbrains.plugins.scala.indices.protocol.jps.JpsCompilationInfo
import spray.json._

import scala.util.{Try, Using}

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
    val baos = new ByteArrayOutputStream(8191)
    val json = data.toJson.compactPrint

    Using(new DeflaterOutputStream(baos))(
      _.write(json.getBytes(StandardCharsets.UTF_8))
    )

    // Compression uses ISO_8859_1 encoding.
    new String(baos.toByteArray, StandardCharsets.ISO_8859_1)
  }

  def decompressCompilationInfo(compressedCompilationInfo: String): Try[JpsCompilationInfo] = {
    // Compression uses ISO_8859_1 encoding.
    val bytes = compressedCompilationInfo.getBytes(StandardCharsets.ISO_8859_1)
    val bais = new ByteArrayInputStream(bytes)

    val inflated =
      Using(new InflaterInputStream(bais)) { inflater =>
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
}
