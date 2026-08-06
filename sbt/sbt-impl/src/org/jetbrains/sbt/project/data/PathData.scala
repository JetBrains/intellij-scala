package org.jetbrains.sbt.project.data

import com.intellij.serialization.PropertyMapping
import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path
import java.util.Objects

/**
 * This is a serializable representation of [[Path]] which can be used by the External System serialization machinery.
 * This class exists to avoid having to deal with raw Strings as paths (which other External System implementations
 * use, like Gradle).
 */
final class PathData @PropertyMapping(Array("pathAsString")) private (private val pathAsString: String) extends Serializable:

  def toPath: Path = Path.of(pathAsString)

  override def equals(obj: Any): Boolean = obj match
    case that: PathData => pathAsString == that.pathAsString
    case _ => false

  override def hashCode(): Int = Objects.hash(pathAsString)

  override def toString: String = s"PathData(path = $pathAsString)"

object PathData:
  def apply(path: Path): PathData = new PathData(path.toCanonicalPath.toString)
