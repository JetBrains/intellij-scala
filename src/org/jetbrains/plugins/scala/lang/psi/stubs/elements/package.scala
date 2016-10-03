package org.jetbrains.plugins.scala.lang.psi.stubs

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubBase, StubInputStream, StubOutputStream}
import com.intellij.util.SofterReference
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

  implicit class StubBaseExt(val stubBase: StubBase[_ <: PsiElement]) extends AnyVal {
    def updateReference[E <: PsiElement](reference: SofterReference[E])
                                        (elementConstructor: (PsiElement, PsiElement) => E): SofterReference[E] =
      updateReferenceWithFilter(reference, elementConstructor) { r =>
        Option(r.get).exists(hasSameContext)
      }

    def updateOptionalReference[E <: PsiElement](reference: SofterReference[Option[E]])
                                                (elementConstructor: (PsiElement, PsiElement) => Option[E]): SofterReference[Option[E]] =
      updateReferenceWithFilter(reference, elementConstructor) {
        _.get match {
          case null => false
          case None => true
          case Some(element) => hasSameContext(element)
        }
      }

    private def updateReferenceWithFilter[T](reference: SofterReference[T],
                                             elementConstructor: (PsiElement, PsiElement) => T)
                                            (filter: SofterReference[T] => Boolean): SofterReference[T] =
      Option(reference).filter {
        filter
      }.getOrElse {
        new SofterReference(elementConstructor(psi, null))
      }

    private def hasSameContext: PsiElement => Boolean = _.getContext eq psi

    private def psi = stubBase.getPsi
  }

}
