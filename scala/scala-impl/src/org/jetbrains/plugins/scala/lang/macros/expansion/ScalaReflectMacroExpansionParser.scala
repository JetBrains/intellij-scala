package org.jetbrains.plugins.scala.lang.macros.expansion

import org.jetbrains.plugins.scala.util.{MacroExpansion, Place}

import java.util.regex.Pattern
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class ScalaReflectMacroExpansionParser(projectName: String) {

  object ParsingState extends Enumeration {
    type ParsingState = Value
    val PLACE, EXPANSION, TREE = Value
  }

  private var parsingState    = ParsingState.PLACE
  private val placeRegex      = Pattern.compile("^performing macro expansion (.+) at source-(.+),line-(\\d+),offset=(\\d+)$", Pattern.DOTALL | Pattern.MULTILINE)
  private val delimRegex      = Pattern.compile("\\s+", Pattern.DOTALL | Pattern.MULTILINE)
  private val treeRegex       = Pattern.compile("^[A-Z][a-z]+\\(.+\\)$")

  var expansions: ArrayBuffer[MacroExpansion] = mutable.ArrayBuffer[MacroExpansion]()

  private def transfer(message: String): Boolean = {
    import ParsingState._
    parsingState match {
      case PLACE =>
        placeRegex.matcher(message).matches
      case EXPANSION if treeRegex.matcher(message).matches() =>
        parsingState = TREE
        true
      case EXPANSION =>
        !delimRegex.matcher(message).matches() && !placeRegex.matcher(message).matches()
      case TREE =>
        !delimRegex.matcher(message).matches() && !placeRegex.matcher(message).matches()
      case _ => false
    }
  }

  def processMessage(message: String): Unit = {
    import ParsingState._

    if (!transfer(message))
      return

    parsingState match {
      case PLACE  =>
        val matcher = placeRegex.matcher(message)
        if (matcher.matches()) {
          expansions += MacroExpansion(
            Place(sourceFile = matcher.group(2), offset = matcher.group(4).toInt)
              (macroApplication = matcher.group(1), line = matcher.group(3).toInt), "")
          parsingState = EXPANSION
        } else reset()
      case EXPANSION  =>
        expansions(expansions.length-1) = MacroExpansion(expansions.last.place, expansions.last.body +"\n"+ message.trim)
      case TREE =>
        expansions(expansions.length-1) = MacroExpansion(expansions.last.place, expansions.last.body, tree = message.trim)
        parsingState = PLACE
    }
  }

  private def reset(): Unit = parsingState = ParsingState.PLACE
}
