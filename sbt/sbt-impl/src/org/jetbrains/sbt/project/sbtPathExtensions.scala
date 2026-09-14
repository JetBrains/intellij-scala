package org.jetbrains.sbt.project

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path
import scala.annotation.tailrec

extension (path: Path)
  def isUnder(root: Path): Boolean = path.isUnder(root, strict = true)

  def isUnder(root: Path, strict: Boolean): Boolean =
    FileUtil.isAncestor(root.toCanonicalPath.toString, path.toCanonicalPath.toString, strict)

  def isOutsideOf(root: Path): Boolean = !path.isUnder(root, strict = false)

  /** Walks `level` parent directories up; `None` when the walk goes past the filesystem root. */
  def <<(level: Int): Option[Path] =
    @tailrec
    def loop(f: Path, l: Int): Option[Path] =
      if f == null then None
      else if l <= 0 then Some(f)
      else loop(f.getParent, l - 1)

    loop(path, level)

  def parent: Option[Path] = Option(path.getParent)

  /** The last segment of the path; `None` if the path has 0 elements. */
  def fileName: Option[String] = Option(path.getFileName).map(_.toString)

  def endsWith(parts: String*): Boolean =
    def endsWith0(file: Path, parts: Seq[String]): Boolean = if (parts.isEmpty) true else
      file.fileName.contains(parts.head) && Option(file.getParent).exists(endsWith0(_, parts.tail))

    endsWith0(path, parts.reverse)

