package org.jetbrains.sbt.project.modifier.location

case class BuildFileEntry[T](file: T, isModuleLocal: Boolean)
