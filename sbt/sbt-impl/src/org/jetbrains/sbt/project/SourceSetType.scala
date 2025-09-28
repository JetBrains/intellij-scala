package org.jetbrains.sbt.project

enum SourceSetType(name: String):
  case Main extends SourceSetType("main")
  case Test extends SourceSetType("test")

  override def toString: String = name
