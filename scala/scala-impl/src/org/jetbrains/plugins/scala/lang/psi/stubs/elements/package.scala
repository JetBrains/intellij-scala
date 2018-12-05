package org.jetbrains.plugins.scala
package lang
package psi
package stubs

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs._
import com.intellij.util.ArrayUtil
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

/**
  * @author adkozlov
  */
package object elements {

  implicit class StubInputStreamExt(val dataStream: StubInputStream) extends AnyVal {
    def readOptionName: Option[String] = {
      val isDefined = dataStream.readBoolean
      if (isDefined) Some(dataStream.readNameString) else None
    }

    def readNames: Array[String] = {
      val length = dataStream.readInt
      (0 until length).map { _ =>
        dataStream.readNameString()
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

  implicit class PsiElementsExt(val elements: Seq[PsiElement]) extends AnyVal {

    def asStrings(transformText: String => String = identity): Array[String] =
      if (elements.nonEmpty) elements
        .map(_.getText)
        .map(transformText)
        .toArray
      else ArrayUtil.EMPTY_STRING_ARRAY
  }

  implicit class IndexSinkExt(val sink: IndexSink) extends AnyVal {

    def occurrences[T <: PsiElement](key: StubIndexKey[String, T],
                                     names: String*): Unit = for {
      name <- names
      if name != null

      cleanName = ScalaNamesUtil.cleanFqn(name)
      if cleanName.nonEmpty
    } sink.occurrence(key, cleanName)
  }
}
