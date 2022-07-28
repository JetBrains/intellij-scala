package org.jetbrains.plugins.scala.decompiler.scalasig

import scala.language.implicitConversions
import scala.reflect.ClassTag

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

class MappedRef[T <: Entry : ClassTag, S <: Entry : ClassTag](val ref: Ref[T], val fun: T => S)
                                                             (implicit override val scalaSig: ScalaSig)
  extends Ref[S](ref.index) {

  override def get: S = fun(ref.get)
}

object Ref {
  def to[T <: Entry : ClassTag](index: Int)(implicit scalaSig: ScalaSig) = new Ref[T](index)

  def unapply[T <: Entry](ref: Ref[T]): Option[T] = Some(ref.get)

  implicit def unwrap[T <: Entry](ref: Ref[T]): T = ref.get

  implicit def unwrapSeq[T <: Entry](refs: Seq[Ref[T]]): Seq[T] = refs.map(_.get)

  implicit def unwrapOption[T <: Entry](ref: Option[Ref[T]]): Option[T] = ref.map(_.get)

  implicit class RefOps[T <: Entry : ClassTag](ref: Ref[T]) {
    import ref.scalaSig

    def map[S <: Entry : ClassTag](fun: T => S): Ref[S] = new MappedRef[T, S](ref, fun)
  }
}

class ScalaDecompilerException(message: String) extends Exception(message)