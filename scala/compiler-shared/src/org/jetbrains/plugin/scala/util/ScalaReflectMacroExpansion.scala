package org.jetbrains.plugin.scala.util

case class Place(macroApplication: String, sourceFile: String, line: Int, offset: Int)
case class MacroExpansion(place: Place, body: String, removeCompanionObject: Boolean = false)
