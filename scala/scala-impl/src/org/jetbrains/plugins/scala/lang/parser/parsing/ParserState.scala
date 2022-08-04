package org.jetbrains.plugins.scala
package lang
package parser
package parsing


sealed abstract class ParserState;

object ParserState {
  case object EMPTY_STATE extends ParserState
  case object FILE_STATE extends ParserState
}