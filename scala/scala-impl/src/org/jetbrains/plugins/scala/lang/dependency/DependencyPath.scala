package org.jetbrains.plugins.scala.lang.dependency

case class DependencyPath(entity: String, maybeMember: Option[String] = None) {

  def asString(wildcardMembers: Boolean = false): String =
    maybeMember.fold(entity) { member =>
      entity + "." + (if (wildcardMembers) "_" else member)
    }
}