package org.jetbrains.plugins.scala.tasty.reader

import dotty.tools.tasty.TastyFormat.header
import dotty.tools.tasty.{TastyReader, UnpickleException}

import java.util.UUID

// See dotty.tools.tasty.TastyHeaderUnpickler

private class HeaderReader(in: TastyReader) {
  import in.*

  def readFullHeader(): TastyHeader = {
    for (i <- header.indices)
      if (readByte() != header(i))
        throw new UnpickleException("not a TASTy file")

    val fileMajor = readNat()
    if (fileMajor <= 27) {
      throw new UnpickleException("TASTy signature has wrong version")
    } else {
      val fileMinor = readNat()
      val fileExperimental = readNat()
      val toolingVersion = {
        val length = readNat()
        val start = currentAddr
        val end = start + length
        goto(end)
        new String(bytes, start.index, length)
      }
      val uuid = new UUID(readUncompressedLong(), readUncompressedLong())
      TastyHeader(uuid, fileMajor, fileMinor, fileExperimental, toolingVersion)
    }
  }
}
