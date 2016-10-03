package org.jetbrains.plugins.scala.lang.psi.stubs

import com.intellij.psi.stubs.{StubInputStream, StubOutputStream}
import com.intellij.util.io.StringRef

/**
  * @author adkozlov
  */
package object elements {

  implicit class StubInputStreamExt(val dataStream: StubInputStream) extends AnyVal {
    def readOptionName: Option[StringRef] = {
      val isDefined = dataStream.readBoolean
      if (isDefined) Some(dataStream.readName) else None
    }
  }

  implicit class StubOutputStreamExt(val dataStream: StubOutputStream) extends AnyVal {
    def writeOptionName(maybeName: Option[String]): Unit = {
      dataStream.writeBoolean(maybeName.isDefined)
      maybeName.foreach {
        dataStream.writeName
      }
    }
  }

  implicit class MaybeStringRefExt(val maybeStringRef: Option[StringRef]) extends AnyVal {
    def asString: Option[String] = maybeStringRef.map {
      StringRef.toString
    }.filter {
      _.nonEmpty
    }
  }

  implicit class MaybeStringExt(val maybeString: Option[String]) extends AnyVal {
    def asReference: Option[StringRef] = maybeString.filter {
      _.nonEmpty
    }.map {
      StringRef.fromString
    }
  }

}
