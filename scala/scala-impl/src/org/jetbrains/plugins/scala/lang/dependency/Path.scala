package org.jetbrains.plugins.scala
package lang
package dependency

/**
  * Pavel Fatin
  */
case class Path(entity: String,
                maybeMember: Option[String] = None) {

  def asString(wildcardMembers: Boolean = false): String =
    maybeMember.fold(entity) { member =>
      entity + "." + (if (wildcardMembers) "_" else member)
    }
}