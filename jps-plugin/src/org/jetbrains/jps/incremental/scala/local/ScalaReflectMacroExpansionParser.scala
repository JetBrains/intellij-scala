package org.jetbrains.jps.incremental.scala.local

import java.io.{ObjectOutputStream, File, FileOutputStream}
import java.util.regex.Pattern

import org.jetbrains.jps.incremental.CompileContext

import scala.collection.mutable

object ScalaReflectMacroExpansionParser {

  case class Place(macroApplication: String, sourceFile: String, line: Int, offset: Int)
  case class MacroExpansion(place: Place, body: String)

  object ParsingState extends Enumeration {
    type ParsingState = Value
    val INIT, PLACE, DELIM, EXPANSION = Value
  }

  var parsingState = ParsingState.INIT

  val placePrefix = "performing macro expansion"
  val delim = "\n"
  val expansionPrefix = "{"

  var expansions = mutable.ListBuffer[MacroExpansion]()

  val placeRegex = Pattern.compile("^performing macro expansion (.+) at source-(.+),line-(\\d+),offset=(\\d+)$", Pattern.DOTALL | Pattern.MULTILINE)
  val expansionRegex = Pattern.compile("^\\s*\\{(.+)\\}\\s+Block.*$", Pattern.DOTALL | Pattern.MULTILINE)

  def isMacroMessage(message: String): Boolean = {
    import ParsingState._
    parsingState match {
      case INIT | EXPANSION => message.startsWith(placePrefix)
      case PLACE            => message == delim
      case DELIM            => message.startsWith(expansionPrefix)
    }
  }

  def processMessage(message: String): Unit = {
    import ParsingState._
    parsingState match {
      case INIT | EXPANSION  =>
        val matcher = placeRegex.matcher(message)
        if (!matcher.matches()) reset()
        else {
          expansions += MacroExpansion(Place(matcher.group(1), matcher.group(2), matcher.group(3).toInt, matcher.group(4).toInt), "")
          parsingState = PLACE
        }
      case PLACE      =>
        if (message != delim) reset()
        else parsingState = DELIM
      case DELIM      =>
        val matcher = expansionRegex.matcher(message)
        if (!matcher.matches()) reset()
        else {
          expansions(expansions.length-1) = MacroExpansion(expansions.last.place, matcher.group(1))
          parsingState = EXPANSION
        }
    }
  }

  def reset() = parsingState = ParsingState.INIT

  def serializeExpansions(context: CompileContext) = {
//    context.getProjectDescriptor.getProject.
    val fo = new FileOutputStream(File.createTempFile(s"expansion-${context.getProjectDescriptor.getProject.getName}", "txt"))
    val so = new ObjectOutputStream(fo)
    for (expansion <- expansions) {
      so.writeObject(expansion)
    }
    so.close()
    fo.close()
  }
}
