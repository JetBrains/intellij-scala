package org.jetbrains

import _root_.org.jetbrains.annotations.NonNls
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.{Pair => IdeaPair}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.{PathUtil, Function => IdeaFunction}

import _root_.java.io._
import _root_.java.lang.{Boolean => JavaBoolean}
import _root_.java.security.MessageDigest
import _root_.java.util.{Optional, ArrayList => JArrayList, List => JList}
import scala.jdk.CollectionConverters._
import scala.language.implicitConversions
import scala.reflect.ClassTag
import scala.util.Using

package object sbt {
  implicit def toIdeaFunction1[A, B](f: A => B): IdeaFunction[A, B] =
    (a: A) => f(a)

  implicit def toIdeaPredicate[A](f: A => Boolean): IdeaFunction[A, JavaBoolean] =
    (a: A) => JavaBoolean.valueOf(f(a))

  implicit def toIdeaFunction2[A, B, C](f: (A, B) => C): IdeaFunction[IdeaPair[A, B], C] =
    (pair: IdeaPair[A, B]) => f(pair.getFirst, pair.getSecond)

  implicit class RichVirtualFile(private val entry: VirtualFile) extends AnyVal {
    def containsDirectory(@NonNls name: String): Boolean = find(name).exists(_.isDirectory)

    def containsFile(@NonNls name: String): Boolean = find(name).exists(_.isFile)

    def find(@NonNls name: String): Option[VirtualFile] = Option(entry.findChild(name))

    def isFile: Boolean = !entry.isDirectory
  }

  implicit class RichString(private val str: String) extends AnyVal {
    def toFile: File = new File(str)
    def shaDigest: String = {
      val digest = MessageDigest.getInstance("SHA1").digest(str.getBytes)
      digest.map("%02x".format(_)).mkString
    }
  }

  implicit class RichBoolean(private val b: Boolean) extends AnyVal {
    def option[A](a: => A): Option[A] = if (b) Some(a) else None

    def either[A, B](right: => B)(left: => A): Either[A, B] = if (b) Right(right) else Left(left)

    def seq[A](a: A*): Seq[A] = if (b) a.toSeq else Seq.empty
  }

  implicit class RichSeq[T](private val xs: Seq[T]) extends AnyVal {
    def distinctBy[A](f: T => A): Seq[T] = {
      val (_, ys) = xs.foldLeft((Set.empty[A], Vector.empty[T])) {
        case ((set, acc), x) =>
          val v = f(x)
          if (set.contains(v)) (set, acc) else (set + v, acc :+ x)
      }
      ys
    }

    def toJavaList: JList[T] = new JArrayList[T](xs.asJava)
  }

  implicit class RichOption[T](private val opt: Option[T]) extends AnyVal {
    // Use for safely checking for null in chained calls
    // TODO: duplicates one from org.jetbrains.sbt.RichOption#safeMap, remove this, do not depend on sbt module for this method
    @inline def safeMap[A](f: T => A): Option[A] = if (opt.isEmpty) None else Option(f(opt.get))

    def toJavaOptional: Optional[T] = opt match {
      case Some(a) => Optional.of(a)
      case None => Optional.empty()
    }
  }

  implicit class RichOptional[T](val opt: Optional[T]) extends AnyVal {
    def asScala: Option[T] = if (opt.isPresent) Some(opt.get) else None
  }

  def jarWith[T: ClassTag]: File = {
    val tClass = implicitly[ClassTag[T]].runtimeClass

    Option(PathUtil.getJarPathForClass(tClass)).map(new File(_)).getOrElse {
      throw new RuntimeException("Jar file not found for class " + tClass.getName)
    }
  }

  def copy(source: File, destination: File): Unit = {
    Using.resource(new BufferedInputStream(new FileInputStream(source))) { in =>
      Using.resource(new BufferedOutputStream(new FileOutputStream(destination))) { out =>
        var eof = false
        while (!eof) {
          val b = in.read()
          if (b == -1) eof = true else out.write(b)
        }
        out.flush()
      }
    }
  }

  def usingTempFile[T](@NonNls prefix: String, suffix: Option[String] = None)(block: File => T): T = {
    val file = FileUtil.createTempFile(prefix, suffix.orNull, true)
    try {
      block(file)
    } finally {
      file.delete()
    }
  }

  def isIdeaPluginEnabled(@NonNls id: String): Boolean = {
    Option(PluginId.findId(id))
      .safeMap(PluginManagerCore.getPlugin)
      .exists(_.isEnabled)
  }
}
