package org.jetbrains.plugins.scala
package lang
package psi
package stubs

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs._
import com.intellij.util.{ArrayFactory, ArrayUtil}
import org.jetbrains.plugins.scala.extensions.{ArrayExt, IterableExt}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

/**
  * @author adkozlov
  */
package object elements {

  implicit class StubInputStreamExt(private val dataStream: StubInputStream) extends AnyVal {
    def readOptionName: Option[String] = {
      val isDefined = dataStream.readBoolean
      if (isDefined) Some(dataStream.readNameString) else None
    }

    def readNames: Array[String] = {
      val length = dataStream.readInt

      new Array[String](length)
        .updateContent(_ => dataStream.readNameString())
    }
  }

  implicit class StubOutputStreamExt(private val dataStream: StubOutputStream) extends AnyVal {
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

  implicit class PsiElementsExt(private val elements: Iterable[PsiElement]) extends AnyVal {
    implicit def stringFactory: ArrayFactory[String] = ArrayUtil.STRING_ARRAY_FACTORY

    def asStrings(transformText: String => String = identity): Array[String] =
      elements.mapToArray(e => transformText(e.getText))
  }

  implicit class IndexSinkExt(private val sink: IndexSink) extends AnyVal {

    def occurrences[T <: PsiElement](key: StubIndexKey[String, T],
                                     names: String*): Unit =
      names.foreach { name =>
        if (name != null) {
          val cleanName = ScalaNamesUtil.cleanFqn(name)
          if (cleanName.nonEmpty) {
            sink.occurrence(key, cleanName)
          }
        }
      }
  }
}
