package org.jetbrains.plugins.scala.tasty

case class TastyFile(text: String, references: Array[ReferenceData], types: Array[TypeData])
