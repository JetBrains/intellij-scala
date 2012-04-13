package org.jetbrains.plugins.scala
package lang.dependency

/**
 * Pavel Fatin
 */

case class Path(entity: String, member: Option[String] = None) {
  def asString: String = asString(wildcardMembers = false)

  def asString(wildcardMembers: Boolean): String = member
          .map(it => "%s.%s".format(entity, if (wildcardMembers) "_" else it))
          .getOrElse(entity)
}

object Path {
  def apply(entity: String, member: String): Path = Path(entity, Some(member))
}

