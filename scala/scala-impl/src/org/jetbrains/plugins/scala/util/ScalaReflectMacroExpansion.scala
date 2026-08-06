package org.jetbrains.plugins.scala.util

case class Place(sourceFile: String, offset: Int)(val macroApplication: String = "", val line: Int = -1)
case class MacroExpansion(place: Place, body: String, tree: String = "", removeCompanionObject: Boolean = false)
