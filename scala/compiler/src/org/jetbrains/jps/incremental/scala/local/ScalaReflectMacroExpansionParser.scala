package org.jetbrains.jps.incremental.scala.local

import java.io.{BufferedOutputStream, ObjectOutputStream, File, FileOutputStream}
import java.util.regex.Pattern

import com.intellij.openapi.application.PathManager
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.plugin.scala.util.{Place, MacroExpansion}

import scala.collection.mutable

object ScalaReflectMacroExpansionParser {

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
//  val expansionRegex = Pattern.compile("^\\s*\\{(.+)\\}\\s+Block.*$", Pattern.DOTALL | Pattern.MULTILINE)
    val expansionRegex = Pattern.compile("^(.+)\\n[^\\n]+$", Pattern.DOTALL | Pattern.MULTILINE)

  def isMacroMessage(message: String): Boolean = {
    import ParsingState._
    parsingState match {
      case INIT | EXPANSION => message.startsWith(placePrefix)
      case PLACE            => message == delim
//      case DELIM            => message.startsWith(expansionPrefix)
      case DELIM            => true
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

  def reset(): Unit = parsingState = ParsingState.INIT

  def serializeExpansions(context: CompileContext): Unit = {
    val file = new File(System.getProperty("java.io.tmpdir") + s"/../../expansion-${context.getProjectDescriptor.getProject.getName}")
    val fo = new BufferedOutputStream(new FileOutputStream(file))
    val so = new ObjectOutputStream(fo)
    for (expansion <- expansions) {
      so.writeObject(expansion)
    }
    so.close()
    fo.close()
  }
}
