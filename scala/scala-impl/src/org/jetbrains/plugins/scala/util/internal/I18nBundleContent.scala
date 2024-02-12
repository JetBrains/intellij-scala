package org.jetbrains.plugins.scala.util.internal

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.util.internal.I18nBundleContent._

import java.io._
import java.nio.charset.StandardCharsets
import java.nio.file.{Path, Paths}
import scala.io.Source

case class I18nBundleContent(entries: Seq[Entry]) {
  def hasKey(key: String): Boolean = entries.exists(_.key == key)

  def sorted: I18nBundleContent =
    copy(entries = entries.sorted)

  def withEntry(entry: Entry): I18nBundleContent = {
    val (before, after) = entries.partition(entryOrdering.lteq(_, entry))
    I18nBundleContent(before ++ Seq(entry) ++ after)
  }

  def writeTo(path: String): Unit =
    writeTo(new PrintWriter(new File(path)))

  def writeTo(outputStream: OutputStream): Unit =
    writeTo(new PrintWriter(outputStream))

  private def writeTo(printWriter: PrintWriter): Unit = try {
    import printWriter._
    var path: String = null
    //make println platform-independent
    def println(s: String = ""): Unit = {
      print(s)
      print("\n")
    }
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

object I18nBundleContent {
  val noPath = "<no-path>"
  val unusedPath = "<unused>"

  implicit val entryOrdering: Ordering[Entry] =
    Ordering.by((entry: Entry) => entry.isUnused -> entry.path)

  case class Entry(key: String, text: String, path: String, comments: String = "") {
    def isUnused: Boolean = path == unusedPath
  }

  def read(bundlePath: String): I18nBundleContent = {
    val lines = {
      val source = Source.fromFile(bundlePath)(StandardCharsets.ISO_8859_1)
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

    new I18nBundleContent(result.result())
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

  /**
   * @param filePath path to the file from which "extract to bundle" action is performed
   */
  private def findBundlePathFor(filePath: String): Option[BundleUsageInfo] =
    for {
      moduleRoot <- moduleRootForFile(filePath)
    } yield {
      val srcRoot      = moduleRoot + "src/"
      val resourceRoot = moduleRoot + "resources/"
      val bundleClassPath = findBundleClass(filePath)
      val bundlePath = bundleClassPath.map(bundleInfo(srcRoot, resourceRoot, _))
      BundleUsageInfo(filePath, srcRoot, resourceRoot, bundlePath)
    }

  private def bundleInfo(srcRoot: String, resourceRoot: String, bundleClassPath: Path) = {
    val className               = withoutExtension(bundleClassPath.getFileName.toString)
    val relPathWithoutExtension = withoutExtension(bundleClassPath.toString.substring(srcRoot.length))
    val qualifiedClassName      = relPathWithoutExtension.replaceAll(raw"[/\\]", ".")
    val withPropertyEnding      = relPathWithoutExtension + ".properties"
    val expectedBundlePath      = resourceRoot + withPropertyEnding
    val bundlePath              = findBundleFileForClass(resourceRoot, expectedBundlePath, className)
    BundleInfo(bundlePath, bundleClassPath.toString, className, qualifiedClassName)
  }

  private def moduleRootForFile(path: String) = {
    assert(new File(path).isFile, s"Expected file: $path")
    val idx = path.indexOf("/src/")
    if (idx < 0) None
    else Some(path.substring(0, idx + 1 /* include the '/' */))
  }

  private def withoutExtension(path: String): String =
    path.substring(0, path.lastIndexOf('.'))

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
      .nextOption()
  }

  private def findBundleFileForClass(resourcePath: String, expectedPath: String, bundleName: String): String = {
    if (new File(expectedPath).isFile) expectedPath
    else {
      // look for
      // resource/messages/<bundleName>.properties OR
      // resource/messages/Scala<bundleName>.properties
      // (at some point in time Scala prefix was added to all bundles)
      val path1 = resourcePath + "messages/" + bundleName + ".properties"
      val path2 = resourcePath + "messages/" + "Scala" + bundleName + ".properties"
      if (new File(path1).isFile)
        path1
      else if (new File(path2).isFile)
        path2
      else
        throw new AssertionError(s"Expected Bundle file $expectedPath or $path1")
    }
  }

  private val nonWordSeq = raw"\W+".r
  private val Trimmed = raw"\W*(.*?)\W*".r
  def convertStringToKey(string: String): String = {
    val maxKeyLength = 60
    val Trimmed(fullKey) = nonWordSeq.replaceAllIn(string, ".").toLowerCase

    lazy val lastDotIdx = fullKey.lastIndexOf(".", maxKeyLength - 3)
    if (fullKey.length < maxKeyLength) fullKey
    else if (lastDotIdx < maxKeyLength - 20) fullKey.substring(0, maxKeyLength - 3) + "..."
    else fullKey.substring(0, lastDotIdx) + "..."
  }

  def escapeText(text: String, hasArgs: Boolean): String = {
    val text1 = text.replace("\\", "\\\\")
    val text2 =
      if (!hasArgs) text1
      else text1.replace("'", "''")
    if (text2.length > 100) text2.replace("\n", "\\n\\\n")
    else text2.replace("\n", "\\n")
  }
}