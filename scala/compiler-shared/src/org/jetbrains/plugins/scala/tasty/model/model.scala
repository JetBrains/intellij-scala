package org.jetbrains.plugins.scala.tasty

package object model {
  // TODO Remove the structural types when the project use Scala 2.13
  type TastyFile = {
    def text: String
    def references: Array[ReferenceData]
    def types: Array[TypeData]
  }
  type Position = {
    def file: String
    def startLine: Int
    def endLine: Int
    def startColumn: Int
    def endColumn: Int
  }
  type ReferenceData = {
    def position: Position
    def target: Position
  }
  type TypeData = {
    def position: Position
    def presentation: String
  }
}
