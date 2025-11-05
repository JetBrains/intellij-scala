package org.jetbrains.sbt.project.structure.data

import java.util.Objects

/**
 * A [[Serializable]] filesystem path which needs to be interpreted in a certain way before transforming it
 * to other data types, e.g. [[java.nio.file.Path]].
 *
 * The main use case is for representing paths that come from remote machines which need to be translated (interpreted)
 * into a different format before using it, i.e. paths that are not safe to use in a local filesystem before some
 * processing is done.
 *
 * The class itself does not offer any way to interpret the path. It is up to the client application to do that.
 *
 * The only way to construct an instance of this class is to implement a [[PathConstructor]] instance.
 * The only way to access the data enclosed in this class is to implement a [[PathInterpreter]] instance.
 */
final class InterpretablePath(private val pathAsString: String)(using PathConstructor.UnsafeConstructorAccess)
  extends Equals with Serializable:
  def unsafePathString(using PathInterpreter.UnsafePathStringAccess): String = pathAsString

  //noinspection InstanceOf
  override def canEqual(that: Any): Boolean = that.isInstanceOf[InterpretablePath]

  override def equals(obj: Any): Boolean = obj match
    case that: InterpretablePath => that.canEqual(this) && this.pathAsString == that.pathAsString
    case _ => false

  override def hashCode(): Int = Objects.hash(pathAsString)

object InterpretablePath:
  def construct[A](a: A)(using constructor: PathConstructor[A]): InterpretablePath = constructor.construct(a)

  given Ordering[InterpretablePath] = (x, y) => x.pathAsString.compare(y.pathAsString)
