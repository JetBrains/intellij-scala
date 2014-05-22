package org.jetbrains

import com.intellij.util.{Function => IdeaFunction, PathUtil}
import com.intellij.openapi.util.{Pair => IdeaPair}
import reflect.ClassTag
import _root_.java.io._
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import _root_.java.lang.{Boolean => JavaBoolean}
import com.intellij.openapi.vfs.{VirtualFile, VfsUtil}
import com.intellij.openapi.util.io.FileUtil

/**
 * @author Pavel Fatin
 */
package object sbt {
  implicit def toIdeaFunction1[A, B](f: A => B): IdeaFunction[A, B] = new IdeaFunction[A, B] {
    def fun(a: A) = f(a)
  }

  implicit def toIdeaPredicate[A](f: A => Boolean): IdeaFunction[A, JavaBoolean] = new IdeaFunction[A, JavaBoolean] {
    def fun(a: A) = JavaBoolean.valueOf(f(a))
  }

  implicit def toIdeaFunction2[A, B, C](f: (A, B) => C): IdeaFunction[IdeaPair[A, B], C] = new IdeaFunction[IdeaPair[A, B], C] {
    def fun(pair: IdeaPair[A, B]) = f(pair.getFirst, pair.getSecond)
  }

  implicit class RichFile(file: File) {
    def /(path: String): File = new File(file, path)

    def `<<`: File = << (1)

    def `<<`(level: Int): File = RichFile.parent(file, level)

    def path: String = file.getPath

    def absolutePath: String = file.getAbsolutePath

    def canonicalPath: String = ExternalSystemApiUtil.toCanonicalPath(file.getAbsolutePath)

    def canonicalFile: File = new File(canonicalPath)

    def url: String = VfsUtil.getUrlForLibraryRoot(file)
    
    def isAncestorOf(aFile: File): Boolean = FileUtil.isAncestor(file, aFile, true)

    def isUnder(root: File): Boolean = FileUtil.isAncestor(root, file, true)

    def isOutsideOf(root: File): Boolean = !FileUtil.isAncestor(root, file, false)

    def write(lines: String*) {
      writeLinesTo(file, lines: _*)
    }

    def copyTo(destination: File) {
      copy(file, destination)
    }
  }

  private object RichFile {
    def parent(file: File, level: Int): File =
      if (level > 0) parent(file.getParentFile, level - 1) else file
  }

  implicit class RichVirtualFile(entry: VirtualFile) {
    def containsDirectory(name: String): Boolean = find(name).exists(_.isDirectory)

    def containsFile(name: String): Boolean = find(name).exists(_.isFile)

    def find(name: String): Option[VirtualFile] = Option(entry.findChild(name))
    
    def isFile: Boolean = !entry.isDirectory
  }

  implicit class RichString(path: String) {
    def toFile: File = new File(path)
  }

  implicit class RichBoolean(val b: Boolean) {
    def option[A](a: => A): Option[A] = if(b) Some(a) else None

    def either[A, B](right: => B)(left: => A): Either[A, B] = if (b) Right(right) else Left(left)

    def seq[A](a: A*): Seq[A] = if (b) Seq(a: _*) else Seq.empty
  }

  implicit class RichSeq[T](xs: Seq[T]) {
    def distinctBy[A](f: T => A): Seq[T] = {
      val (_, ys) = xs.foldLeft((Set.empty[A], Vector.empty[T])) {
        case ((set, acc), x) =>
          val v = f(x)
          if (set.contains(v)) (set, acc) else (set + v, acc :+ x)
      }
      ys
    }
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
      lines.foreach(writer.println(_))
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
}
