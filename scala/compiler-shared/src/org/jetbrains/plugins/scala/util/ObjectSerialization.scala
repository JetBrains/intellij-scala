package org.jetbrains.plugins.scala.util

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}
import java.util.Base64

import com.intellij.openapi.util.io.FileUtil

import scala.util.Using
object ObjectSerialization {

  def toBytes(obj: Any): Array[Byte] =
    Using.resource(new ByteArrayOutputStream()) { buffer =>
      Using.resource(new ObjectOutputStream(buffer)) { stream =>
        stream.writeObject(obj)
        buffer.toByteArray
      }
    }

  def fromBytes[A](bytes: Array[Byte]): A =
    Using.resource(new ByteArrayInputStream(bytes)) { buffer =>
      Using.resource(new ObjectInputStream(buffer)) { stream =>
        val obj = stream.readObject().asInstanceOf[A]
        if (stream.available > 0) {
          val excess = FileUtil.loadTextAndClose(stream)
          throw new IllegalArgumentException("Excess bytes after event deserialization: " + excess)
        }
        obj
      }
    }

  def toBase64(obj: Any): String =
    Base64.getEncoder.encodeToString(toBytes(obj))

  def fromBase64[A](base64: String): A =
    fromBytes(Base64.getDecoder.decode(base64))
}
