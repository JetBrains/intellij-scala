package org.jetbrains.plugin.scala.compilerReferences

import java.io._
import java.util.Scanner

import org.jetbrains.jps.incremental.CompiledClass
import org.jetbrains.jps.incremental.scala.local.LazyCompiledClass

import scala.util.Try

trait Codec[T] {
  def encode(t:  T)(implicit ctx: StringBuilder): Unit
  def decode(in: Scanner): Option[T]
}

object Codec {
  private[this] val delimiter: String = "\u0000"

  def apply[T](implicit codec: Codec[T]): Codec[T] = codec

  implicit class CodecOps[T](val t: T) extends AnyVal {
    def encode(implicit ev: Codec[T]): String = {
      val builder = StringBuilder.newBuilder
      ev.encode(t)(builder)
      builder.mkString
    }
  }

  implicit class StringCodecOps(val s: String) extends AnyVal {
    def decode[T: Codec]: Option[T] = {
      val scanner = new Scanner(s).useDelimiter(delimiter)
      scanner.decode[T]
    }
  }

  private[this] implicit class ScanerOps(val in: Scanner) extends AnyVal {
    def decode[T](implicit ev: Codec[T]): Option[T] = ev.decode(in)
  }
  
  def simpleCodec[T](decoder: String => T): Codec[T] = new Codec[T] {
    override def encode(t: T)(implicit ctx: StringBuilder): Unit = {
      ctx ++= t.toString
      ctx ++= delimiter
    }

    override def decode(in: Scanner): Option[T] = Try(decoder(in.next)).toOption
  }

  implicit val stringCodec: Codec[String]   = simpleCodec(identity)
  implicit val booleanCodec: Codec[Boolean] = simpleCodec(_.toBoolean)
  implicit val longCodec: Codec[Long]       = simpleCodec(_.toLong)
  implicit val intCodec: Codec[Int]         = simpleCodec(_.toInt)

  def iterableCodec[T: Codec, R <: Iterable[T]](reify: Iterable[T] => R): Codec[R] = new Codec[R] {
    override def encode(t: R)(implicit ctx: StringBuilder): Unit = {
      Codec[Int].encode(t.size)
      t.foreach(Codec[T].encode)
    }

    override def decode(in: Scanner): Option[R] =
      for {
        length <- in.decode[Int]
      } yield reify((0 until length).flatMap(_ => in.decode[T]))
  }

  implicit def seqCodec[T: Codec]: Codec[Seq[T]] = iterableCodec[T, Seq[T]](_.toSeq)
  implicit def setCodec[T: Codec]: Codec[Set[T]] = iterableCodec[T, Set[T]](_.toSet)

  implicit val compiledClassInfoCodec: Codec[CompiledClass] = new Codec[CompiledClass] {
    override def encode(t: CompiledClass)(implicit ctx: StringBuilder): Unit =
      Seq(t.getOutputFile.getPath, t.getSourceFile.getPath, t.getClassName).foreach(Codec[String].encode)

    override def decode(in: Scanner): Option[CompiledClass] =
      for {
        output     <- in.decode[String]
        sourceFile <- in.decode[String]
        className  <- in.decode[String]
      } yield new LazyCompiledClass(new File(output), new File(sourceFile), className)
  }

  implicit val buildDataCodec: Codec[BuildData] = new Codec[BuildData] {
    override def encode(t: BuildData)(implicit ctx: StringBuilder): Unit = {
      Codec[Long].encode(t.timeStamp)
      Codec[Set[CompiledClass]].encode(t.compiledClasses)
      Codec[Set[String]].encode(t.removedSources)
      Codec[Set[String]].encode(t.affectedModules)
      Codec[Boolean].encode(t.isCleanBuild)
    }

    override def decode(in: Scanner): Option[BuildData] =
      for {
        timeStamp       <- in.decode[Long]
        compiledClasses <- in.decode[Set[CompiledClass]]
        removedSources  <- in.decode[Set[String]]
        affectedModules <- in.decode[Set[String]]
        isRebuild       <- in.decode[Boolean]
      } yield
        BuildData(
          timeStamp,
          compiledClasses,
          removedSources,
          affectedModules,
          isRebuild
        )
  }
}
