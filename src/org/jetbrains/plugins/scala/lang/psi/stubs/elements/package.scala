package org.jetbrains.plugins.scala.lang.psi.stubs

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs._
import com.intellij.util.io.StringRef
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys.IMPLICITS_KEY
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil._

/**
  * @author adkozlov
  */
package object elements {

  implicit class StubInputStreamExt(val dataStream: StubInputStream) extends AnyVal {
    def readOptionName: Option[StringRef] = {
      val isDefined = dataStream.readBoolean
      if (isDefined) Some(dataStream.readName) else None
    }

    def readNames: Array[StringRef] = {
      val length = dataStream.readInt
      (0 until length).map { _ =>
        dataStream.readName
      }.toArray
    }
  }

  implicit class StubOutputStreamExt(val dataStream: StubOutputStream) extends AnyVal {
    def writeOptionName(maybeName: Option[String]): Unit = {
      dataStream.writeBoolean(maybeName.isDefined)
      maybeName.foreach {
        dataStream.writeName
      }
    }

    def writeNames(names: Array[String]): Unit = {
      dataStream.writeInt(names.length)
      names.foreach {
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

  implicit class StringRefArrayExt(val stringRefs: Array[StringRef]) extends AnyVal {
    def asStrings: Array[String] = stringRefs.map {
      StringRef.toString
    }.filter {
      _.nonEmpty
    }
  }

  implicit class StringArrayExt(val strings: Array[String]) extends AnyVal {
    def asReferences: Array[StringRef] = strings.filter {
      _.nonEmpty
    }.map {
      StringRef.fromString
    }
  }

  implicit class SerializerExt[S <: StubElement[T], T <: PsiElement](val serializer: ScStubElementType[S, T]) extends AnyVal {
    def indexStub(names: Array[String], sink: IndexSink, key: StubIndexKey[String, _ <: T]): Unit =
      names.filter {
        _ != null
      }.map {
        cleanFqn
      }.filter {
        _.nonEmpty
      }.foreach {
        sink.occurrence(key, _)
      }

    def indexImplicit(sink: IndexSink): Unit =
      sink.occurrence(IMPLICITS_KEY, "implicit")
  }

}
