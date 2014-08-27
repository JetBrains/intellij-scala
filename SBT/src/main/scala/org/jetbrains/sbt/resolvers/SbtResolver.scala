package org.jetbrains.sbt
package resolvers

import java.util.regex.Pattern

/**
 * @author Nikolay Obedin
 * @since 7/25/14.
 */
case class SbtResolver(name: String, root: String) {
  import SbtResolver._

  def associatedIndex = SbtResolverIndexesManager().find(this)

  override def toString = s"$root$DELIMITER$name"
}

object SbtResolver {
  val DELIMITER = "|"
  def fromString(str: String): Option[SbtResolver] = {
    str.split(Pattern.quote(DELIMITER), 2).toSeq match {
      case Seq(root, name) => Some(new SbtResolver(name, root))
      case Seq(root) => Some(new SbtResolver("", root))
      case _ => None
    }
  }
}

