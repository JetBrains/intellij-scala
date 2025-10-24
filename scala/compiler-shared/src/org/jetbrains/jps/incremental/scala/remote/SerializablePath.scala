package org.jetbrains.jps.incremental.scala.remote

import java.nio.file.{Path, Paths}
import java.util.Objects

/**
 * @note The reason this class is not a `case class` is that we do not want to expose the implementation details via
 *       the automatically generated `unapply` method, which would allow access to the inner path represented as a
 *       [[String]]. Instead, we always want to go to and from [[Path]].
 */
final class SerializablePath private (private val pathAsString: String) extends Serializable {
  override def hashCode(): Int = Objects.hash(pathAsString)

  override def equals(obj: Any): Boolean = obj match {
    case that: SerializablePath => pathAsString == that.pathAsString
    case _ => false
  }

  override def toString: String = s"SerializablePath(path = $pathAsString)"

  def toPath: Path = Paths.get(pathAsString)
}

object SerializablePath {
  def apply(path: Path): SerializablePath = new SerializablePath(path.toAbsolutePath.normalize().toString)

  def unapply(path: SerializablePath): Some[Path] = Some(path.toPath)
}
