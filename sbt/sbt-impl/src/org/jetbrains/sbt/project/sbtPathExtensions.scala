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

  def <<(level: Int): Path =
    @tailrec
    def loop(f: Path, l: Int): Path =
      if f == null || l <= 0 then f
      else loop(f.getParent, l - 1)

    loop(path, level)

  def parent: Option[Path] = Option(path.getParent)

  def endsWith(parts: String*): Boolean =
    def endsWith0(file: Path, parts: Seq[String]): Boolean = if (parts.isEmpty) true else
      parts.head == file.getFileName.toString && Option(file.getParent).exists(endsWith0(_, parts.tail))

    endsWith0(path, parts.reverse)

