package org.jetbrains

import _root_.java.io._
import _root_.java.lang.{Boolean => JavaBoolean}
import _root_.java.security.MessageDigest

import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.{Computable, Pair => IdeaPair}
import com.intellij.openapi.vfs.{VfsUtil, VirtualFile}
import com.intellij.util.{PathUtil, Function => IdeaFunction}
import plugins.scala.extensions.ToNullSafe

import scala.annotation.tailrec
import scala.language.implicitConversions
import scala.reflect.ClassTag

/**
 * @author Pavel Fatin
 */
package object sbt {
  implicit def toIdeaFunction1[A, B](f: A => B): IdeaFunction[A, B] = new IdeaFunction[A, B] {
    def fun(a: A): B = f(a)
  }

  implicit def toIdeaPredicate[A](f: A => Boolean): IdeaFunction[A, JavaBoolean] = new IdeaFunction[A, JavaBoolean] {
    def fun(a: A): JavaBoolean = JavaBoolean.valueOf(f(a))
  }

  implicit def toIdeaFunction2[A, B, C](f: (A, B) => C): IdeaFunction[IdeaPair[A, B], C] = new IdeaFunction[IdeaPair[A, B], C] {
    def fun(pair: IdeaPair[A, B]): C = f(pair.getFirst, pair.getSecond)
  }

  implicit class RichFile(val file: File) {

    def /(path: String): File = new File(file, path)

    def `<<`: File = << (1)

    def `<<`(level: Int): File = RichFile.parent(file, level)

    def name: String = file.getName

    def path: String = file.getPath

    def absolutePath: String = file.getAbsolutePath

    def canonicalPath: String = ExternalSystemApiUtil.toCanonicalPath(file.getAbsolutePath)

    def canonicalFile: File = new File(canonicalPath)

    def parent: Option[File] = Option(file.getParentFile)

    def endsWith(parts: String*): Boolean = endsWith0(file, parts.reverse)

    private def endsWith0(file: File, parts: Seq[String]): Boolean = if (parts.isEmpty) true else
      parts.head == file.getName && Option(file.getParentFile).exists(endsWith0(_, parts.tail))

    def url: String = VfsUtil.getUrlForLibraryRoot(file)
    
    def isAncestorOf(aFile: File): Boolean = FileUtil.isAncestor(file, aFile, true)

    def isUnder(root: File): Boolean = FileUtil.isAncestor(root, file, true)

    def isIn(root: File): Boolean = file.getParentFile == root

    def isOutsideOf(root: File): Boolean = !FileUtil.isAncestor(root, file, false)

    def write(lines: String*) {
      writeLinesTo(file, lines: _*)
    }

    def copyTo(destination: File) {
      copy(file, destination)
    }

    def ls(filter: String => Boolean): Seq[File] = {
      val files = file.listFiles()
      assert(files != null, file.getPath)
      files.filter(file => filter(file.getName)).toSeq
    }
  }

  private object RichFile {
    @tailrec
    def parent(file: File, level: Int): File =
      if (level > 0) parent(file.getParentFile, level - 1) else file
  }

  implicit class RichVirtualFile(val entry: VirtualFile) extends AnyVal {
    def containsDirectory(name: String): Boolean = find(name).exists(_.isDirectory)

    def containsFile(name: String): Boolean = find(name).exists(_.isFile)

    def find(name: String): Option[VirtualFile] = Option(entry.findChild(name))
    
    def isFile: Boolean = !entry.isDirectory
  }

  implicit class RichString(val str: String) extends AnyVal {
    def toFile: File = new File(str)
    def shaDigest: String = {
      val digest = MessageDigest.getInstance("SHA1").digest(str.getBytes)
      digest.map("%02x".format(_)).mkString
    }
  }

  implicit class RichBoolean(val b: Boolean) extends AnyVal {
    def option[A](a: => A): Option[A] = if(b) Some(a) else None

    def either[A, B](right: => B)(left: => A): Either[A, B] = if (b) Right(right) else Left(left)

    def seq[A](a: A*): Seq[A] = if (b) Seq(a: _*) else Seq.empty
  }

  implicit class RichSeq[T](val xs: Seq[T]) extends AnyVal {
    def distinctBy[A](f: T => A): Seq[T] = {
      val (_, ys) = xs.foldLeft((Set.empty[A], Vector.empty[T])) {
        case ((set, acc), x) =>
          val v = f(x)
          if (set.contains(v)) (set, acc) else (set + v, acc :+ x)
      }
      ys
    }
  }

  implicit class RichOption[T](val opt: Option[T]) extends AnyVal {
    // Use for safely checking for null in chained calls
    @inline def safeMap[A](f: T => A): Option[A] = if (opt.isEmpty) None else Option(f(opt.get))
  }

  def jarWith[T : ClassTag]: File = {
    val tClass = implicitly[ClassTag[T]].runtimeClass

    Option(PathUtil.getJarPathForClass(tClass)).map(new File(_)).getOrElse {
      throw new RuntimeException("Jar file not found for class " + tClass.getName)
    }
  }

  def using[A <: Closeable, B](resource: A)(block: A => B): B = {
    try {
      block(resource)
    } finally {
      resource.close()
    }
  }

  def writeLinesTo(file: File, lines: String*) {
    using(new PrintWriter(new FileWriter(file))) { writer =>
      lines.foreach(writer.println)
      writer.flush()
    }
  }

  def copy(source: File, destination: File) {
    using(new BufferedInputStream(new FileInputStream(source))) { in =>
      using(new BufferedOutputStream(new FileOutputStream(destination))) { out =>
        var eof = false
        while (!eof) {
          val b = in.read()
          if (b == -1) eof = true else out.write(b)
        }
        out.flush()
      }
    }
  }

  def usingTempFile[T](prefix: String, suffix: Option[String] = None)(block: File => T): T = {
    val file = FileUtil.createTempFile(prefix, suffix.orNull, true)
    try {
      block(file)
    } finally {
      file.delete()
    }
  }

  private val NameWithExtension = """(.+)(\..+?)""".r

  private def parse(fileName: String): (String, String) = fileName match {
    case NameWithExtension(name, extension) => (name, extension)
    case name => (name, "")
  }

  def inWriteAction[T](body: => T): T = {
    ApplicationManager.getApplication.runWriteAction(new Computable[T] {
      def compute: T = body
    })
  }

  def isIdeaPluginEnabled(id: String): Boolean =
    PluginId.findId(id).nullSafe
      .map(PluginManager.getPlugin)
      .exists(_.isEnabled)
}
