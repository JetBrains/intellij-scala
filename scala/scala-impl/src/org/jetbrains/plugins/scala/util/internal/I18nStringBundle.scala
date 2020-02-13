package org.jetbrains.plugins.scala
package util
package internal

import java.io._
import java.nio.file.{Path, Paths}

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.util.internal.I18nStringBundle._

import scala.io.Source

case class I18nStringBundle(entries: Seq[Entry]) {
  def hasKey(key: String): Boolean = entries.exists(_.key == key)

  def sorted: I18nStringBundle =
    copy(entries = entries.sorted)

  def withEntry(entry: Entry): I18nStringBundle = {
    val (before, after) = entries.partition(entryOrdering.lteq(_, entry))
    I18nStringBundle(before ++ Seq(entry) ++ after)
  }

  def isCorrectlySorted: Boolean =
    this == sorted

  def writeTo(path: String): Unit =
    writeTo(new PrintWriter(new File(path)))

  def writeTo(outputStream: OutputStream): Unit =
    writeTo(new PrintWriter(outputStream))

  private def writeTo(printWriter: PrintWriter): Unit = try {
    import printWriter._
    var path: String = null
    for (entry <- entries) {
      if (path != entry.path) {
        if (path != null) {
          println()
        }
        path = entry.path
        println(PathHeader.prefix + path)
      }
      print(entry.comments)
      println(entry.key + "=" + entry.text)
    }
  } finally printWriter.close()
}

object I18nStringBundle {
  val noPath = "<no-path>"
  val unusedPath = "<unused>"

  implicit val entryOrdering: Ordering[Entry] =
    Ordering.by((entry: Entry) => entry.isUnused -> entry.path)

  case class Entry(key: String, text: String, path: String, comments: String = "") {
    def isUnused: Boolean = path == unusedPath
  }

  def readBundle(bundlepath: String): I18nStringBundle = {
    val lines = {
      val source = Source.fromFile(bundlepath)
      try source.getLines().toArray
      finally source.close()
    }

    var comments = ""
    var path = noPath
    val result = Seq.newBuilder[Entry]

    var i = 0
    while (lines.indices contains i) {
      val line = lines(i)
      line match {
        case PathHeader(newPath) =>
          if (comments != "")
            throw new Exception(s"Comment might get lost: '$comments'")
          path = newPath

        case _ if line.startsWith("#") =>
          comments += line + "\n"

        case _ if line.contains("=") =>
          var (key, value) = line.split("=").toSeq match { case key +: rest => key -> rest.mkString("=")}
          while (value.last == '\\') {
            i += 1
            value = value + "\n" + lines(i)
          }
          result += Entry(key, value, path, comments)
          comments = ""
        case _ =>
      }
      i += 1
    }

    new I18nStringBundle(result.result())
  }

  private object PathHeader {
    val prefix = "### "
    def unapply(line: String): Option[String] = {
      val isNewPath = line.startsWith(prefix) &&
        (line.contains('.') && line.contains('/') || line.contains(unusedPath))
      isNewPath.option(line.substring(prefix.length))
    }
  }

  case class BundleInfo(bundleFilePath: String, bundleClassPath: String, bundleClassName: String, bundleQualifiedClassName: String)
  case class BundleUsageInfo(originalPath: String, moduleSrcRoot: String, moduleResourceRoot: String, bundleInfo: Option[BundleInfo])

  def findBundlePathFor(path: String): Option[BundleUsageInfo] = for {
    moduleRoot <- {
        val idx = path.indexOf("/src/")
        if (idx < 0) None
        else Some(path.substring(0, idx + 1 /* include the '/' */))
      }
    srcRoot = moduleRoot + "src/"
  } yield {
    def withoutExtension(path: String): String = path.substring(0, path.lastIndexOf('.'))
    val resourceRoot = moduleRoot + "resources/"
    val bundlePath = for {
      bundleClassPath <- findBundleClass(path)
      relPath = bundleClassPath.toString.substring(srcRoot.length)
      className = withoutExtension(bundleClassPath.getFileName.toString)
      relPathWithoutExtension = withoutExtension(relPath)
      qualifiedClassName = relPathWithoutExtension.replaceAll(raw"[/\\]", ".")
      withPropertyEnding = relPathWithoutExtension + ".properties"
      bundlePath = resourceRoot + withPropertyEnding
    } yield BundleInfo(bundlePath, bundleClassPath.toString, className, qualifiedClassName)
    BundleUsageInfo(path, srcRoot, resourceRoot, bundlePath)
  }

  def findBundlePathFor(element: PsiElement): Option[BundleUsageInfo] =
    element.containingVirtualFile.map(_.getPath).flatMap(findBundlePathFor)

  private val bundleClassRegex =
    raw".*Bundle\.(java|scala)".r
  private def findBundleClass(_path: String): Option[Path] = {
    Paths.get(_path)
      .parents
      .flatMap { path =>
        new File(path.toUri)
          .list()
          .map(Paths.get(path.toString, _))
          .find { path =>
            val fileName = path.getFileName.toString
            bundleClassRegex.findFirstIn(fileName).isDefined
          }
      }
      .headOption
  }
}