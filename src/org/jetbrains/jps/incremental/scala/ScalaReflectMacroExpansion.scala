package org.jetbrains.jps.incremental.scala

@SerialVersionUID(-7235816150144567870L)
case class Place(macroApplication: String, sourceFile: String, line: Int, offset: Int)

@SerialVersionUID(-2574816150794617870L)
case class MacroExpansion(place: Place, body: String)
