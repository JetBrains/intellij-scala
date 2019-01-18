package org.jetbrains.jps.incremental.scala.remote


import java.io._
import java.util.Base64
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

object Protocol {
  private val argSeparator = "#ARG#"
  private val emptyArg = "#STUB#"

  def serializeArgs(args: Seq[String]): Array[Byte] = {
    toBase64(compress(toBytes(mergeArgs(escapeEmptyArgs(args)))))
  }

  def deserializeArgs(data: Seq[String]): Seq[String] = {
    val Seq(mergedEncodedArgs) = data
    unescapeEmptyArgs(separateArgs(fromBytes(decompress(fromBase64(toBytes(mergedEncodedArgs))))))
  }

  def serializeEvent(event: Event, standalone: Boolean): Array[Byte] = {
    adjustBase64(standalone, toBase64(compress(event.toBytes)))
  }

  def deserializeEvent(data: Array[Byte]): Event = {
    Event.fromBytes(decompress(fromBase64(data)))
  }

  private def compress(bytes: Array[Byte]): Array[Byte] = {
    val bytesStream = new ByteArrayOutputStream(bytes.length)
    val deflaterStream = new DeflaterOutputStream(bytesStream)
    deflaterStream.write(bytes)
    deflaterStream.close()
    bytesStream.toByteArray
  }

  private def decompress(bytes: Array[Byte]): Array[Byte] = {
    val inflaterStream = new InflaterInputStream(new ByteArrayInputStream(bytes))
    val byteStream = new ByteArrayOutputStream
    var read = 0
    val buffer = new Array[Byte](8192)
    while ({ read = inflaterStream.read(buffer); read > 0 }) {
      byteStream.write(buffer, 0, read)
    }
    inflaterStream.close()
    byteStream.toByteArray
  }

  def toBytes(string: String): Array[Byte] = {
    string.getBytes("UTF-8")
  }

  def fromBytes(bytes: Array[Byte]): String = {
    new String(bytes, "UTF-8")
  }

  private def toBase64(bytes: Array[Byte]): Array[Byte] = {
    Base64.getEncoder.encode(bytes)
  }

  private def fromBase64(encoded: Array[Byte]): Array[Byte] = {
    Base64.getDecoder.decode(encoded)
  }

  private def escapeEmptyArgs(args: Seq[String]) = {
    args.map(arg => if (arg.isEmpty) emptyArg else arg)
  }
  private def unescapeEmptyArgs(args: Seq[String]) = {
    args.map(arg => if (arg == emptyArg) "" else arg)
  }

  private def mergeArgs(argsToMerge: Seq[String]) = {
    argsToMerge.mkString(Protocol.argSeparator)
  }

  private def separateArgs(mergedArgs: String) = {
    mergedArgs.split(Protocol.argSeparator).toSeq
  }

  private def adjustBase64(standalone: Boolean, bytes: Array[Byte]) = {
    val string = fromBytes(bytes)
    val adjusted = if (standalone && !string.endsWith("=")) string + "=" else string
    toBytes(adjusted)
  }

}