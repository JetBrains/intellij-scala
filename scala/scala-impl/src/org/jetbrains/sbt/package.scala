package org.jetbrains

import _root_.org.jetbrains.annotations.{NonNls, Nullable}
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.{Pair => IdeaPair}
import com.intellij.openapi.vfs.{VfsUtil, VirtualFile}
import com.intellij.util.{PathUtil, Function => IdeaFunction}

import _root_.java.io._
import _root_.java.lang.{Boolean => JavaBoolean}
import _root_.java.security.MessageDigest
import _root_.java.util.{Optional, ArrayList => JArrayList, List => JList}
import scala.annotation.tailrec
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

  implicit class RichFile(private val file: File) {

    def /(@NonNls path: String): File = new File(file, path)

    def `<<`: File = <<(1)

    def `<<`(level: Int): File = RichFile.parent(file, level)

    @NonNls def name: String = file.getName

    @NonNls def path: String = file.getPath

    @NonNls def absolutePath: String = file.getAbsolutePath

    @NonNls def canonicalPath: String = ExternalSystemApiUtil.toCanonicalPath(file.getAbsolutePath)

    def canonicalFile: File = new File(canonicalPath)

    def parent: Option[File] = Option(file.getParentFile)

    def parent(level: Int): Option[File] = Option(RichFile.parent(file, level))

    def maybeFile: Option[File] = Option(file).filter(_.isFile)

    def maybeDir: Option[File] = Option(file).filter(_.isDirectory)

    def endsWith(parts: String*): Boolean = endsWith0(file, parts.reverse)

    private def endsWith0(file: File, parts: Seq[String]): Boolean = if (parts.isEmpty) true else
      parts.head == file.getName && Option(file.getParentFile).exists(endsWith0(_, parts.tail))

    def url: String = VfsUtil.getUrlForLibraryRoot(file)

    def isAncestorOf(aFile: File): Boolean = FileUtil.isAncestor(file, aFile, true)

    def isUnder(root: File): Boolean = FileUtil.isAncestor(root, file, true)

    def isIn(root: File): Boolean = file.getParentFile == root

    def isOutsideOf(root: File): Boolean = !FileUtil.isAncestor(root, file, false)

    def copyTo(destination: File): Unit = {
      copy(file, destination)
    }

    def ls(filter: String => Boolean): Seq[File] =
      if (file.isDirectory) file.listFiles().filter(file => filter(file.getName)).toSeq
      else Seq.empty
  }

  private object RichFile {
    @Nullable
    @tailrec
    def parent(@Nullable file: File, level: Int): File =
      if (level > 0 && file != null) parent(file.getParentFile, level - 1)
      else file
  }

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

    def seq[A](a: A*): Seq[A] = if (b) Seq(a: _*) else Seq.empty
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
    import plugins.scala.extensions.ToNullSafe
    PluginId.findId(id).nullSafe
      .map(PluginManagerCore.getPlugin)
      .exists(_.isEnabled)
  }
}
