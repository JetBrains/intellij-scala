package org.jetbrains.sbt.shell

import java.io.{BufferedOutputStream, File, FileOutputStream, ObjectOutputStream}
import java.util.regex.Pattern

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.plugin.scala.util.{MacroExpansion, Place}

import scala.collection.mutable

class ScalaReflectMacroExpansionParser(projectName: String) {

  object ParsingState extends Enumeration {
    type ParsingState = Value
    val PLACE, EXPANSION, TREE = Value
  }

  private var parsingState    = ParsingState.PLACE
  private var expansions      = mutable.ArrayBuffer[MacroExpansion]()
  private val placeRegex      = Pattern.compile("^performing macro expansion (.+) at source-(.+),line-(\\d+),offset=(\\d+)$", Pattern.DOTALL | Pattern.MULTILINE)
  private val delimRegex      = Pattern.compile("\\s+", Pattern.DOTALL | Pattern.MULTILINE)
  private val cache           = new File(System.getProperty("java.io.tmpdir") + s"/expansion-$projectName")

  FileUtil.delete(cache)

  def isMacroMessage(message: String): Boolean = {
    import ParsingState._
    parsingState match {
      case PLACE => placeRegex.matcher(message).matches()
      case EXPANSION => !delimRegex.matcher(message).matches() && !placeRegex.matcher(message).matches()
      case TREE => !delimRegex.matcher(message).matches() && !placeRegex.matcher(message).matches()
    }
  }

  def processMessage(message: String): Unit = {
    import ParsingState._
    parsingState match {
      case PLACE  =>
        val matcher = placeRegex.matcher(message)
        if (matcher.matches()) {
          expansions += MacroExpansion(Place(matcher.group(1), matcher.group(2), matcher.group(3).toInt, matcher.group(4).toInt), "")
          parsingState = EXPANSION
        } else reset()
      case EXPANSION  =>
        expansions(expansions.length-1) = MacroExpansion(expansions.last.place, message.trim)
        parsingState = TREE
      case TREE =>
        parsingState = PLACE
    }
  }

  private def reset(): Unit = parsingState = ParsingState.PLACE

  def serializeExpansions(): Unit = {
    val fo = new BufferedOutputStream(new FileOutputStream(cache))
    val so = new ObjectOutputStream(fo)
    for (expansion <- expansions) {
      so.writeObject(expansion)
    }
    so.close()
    fo.close()
  }
}
