package org.jetbrains.plugins.scala.decompiler.scalasig

import scala.language.implicitConversions
import scala.reflect.ClassTag

/**
  * Nikolay.Tropin
  * 19-Jul-17
  */
class Ref[T <: Entry : ClassTag](val index: Int)(implicit val scalaSig: ScalaSig) {

  def get: T = {
    val entry = scalaSig.get(index)

    if (!scalaSig.isInitialized)
      throw new ScalaDecompilerException("usage of scalaSig entry before initialization")

    val expectedClass = implicitly[ClassTag[T]].runtimeClass
    if (!expectedClass.isInstance(entry)) {
      val expName = expectedClass.getCanonicalName
      val actName = entry.getClass.getCanonicalName
      val message =
        s"wrong type of reference at index $index, expected: $expName, actual: $actName"
      throw new ScalaDecompilerException(message)
    }

    entry.asInstanceOf[T]
  }

  override def toString: String =
    if (!scalaSig.isInitialized) "not initialized"
    else get.toString

  override def equals(obj: scala.Any): Boolean = obj match {
    case r: Ref[_] => r.index == index
    case _ => false
  }

  override def hashCode(): Int = index
}

object Ref {
  def to[T <: Entry : ClassTag](index: Int)(implicit scalaSig: ScalaSig) = new Ref[T](index)

  def unapply[T <: Entry](ref: Ref[T]): Option[T] = Some(ref.get)

  implicit def unwrap[T <: Entry](ref: Ref[T]): T = ref.get

  implicit def unwrapSeq[T <: Entry](refs: Seq[Ref[T]]): Seq[T] = refs.map(_.get)

  implicit def unwrapOption[T <: Entry](ref: Option[Ref[T]]): Option[T] = ref.map(_.get)
}

class ScalaDecompilerException(message: String) extends Exception(message)