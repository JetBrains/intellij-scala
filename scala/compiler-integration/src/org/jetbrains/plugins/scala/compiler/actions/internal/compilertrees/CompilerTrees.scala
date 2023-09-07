package org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees

import org.jetbrains.jps.incremental.scala.{Client, MessageKind}
import org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees.CompilerTrees.PhaseWithTreeText
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel

import scala.collection.mutable.ArrayBuffer

final class CompilerTrees(
  val phasesTrees: Seq[PhaseWithTreeText]
) {
  lazy val allPhasesTextConcatenated: String = phasesTrees
    .map { pt =>
      s"""// Phase: ${pt.phase}
         |${pt.treeText}""".stripMargin
    }
    .mkString("\n\n")
}

object CompilerTrees {

  case class PhaseWithTreeText(phase: String, treeText: String)

  def parseFromCompilerMessages(
    messages: Seq[Client.ClientMsg],
    languageLevel: ScalaLanguageLevel,
  ): CompilerTrees = {
    if (languageLevel.isScala3) {
      parseForScala3(messages)
    }
    else {
      parseForScala2(messages)
    }
  }

  /**
   * In Scala 2 compiler tree messages are printed as warnings, without pointer to file position.<br>
   * It contains phase and tree in different warning messages.<br>
   * Between those messages there can be some other warning messages:
   *  - compiled file name (usually only after parser phase)
   *  - saying something like "tree is unchanged since parser" (but not after each phase, for some reason)
   *  - ordinary scala code warnings (e.g. deprecation or non-exhaustive match, etc...)
   */
  private def parseForScala2(messages: Seq[Client.ClientMsg]): CompilerTrees = {
    val warnings = messages.filter { message =>
      message.kind == MessageKind.Warning &&
        message.pointer.isEmpty
    }

    val buffer = ArrayBuffer.empty[PhaseWithTreeText]

    val iterator = warnings.iterator
    var currentPhase: String = null
    var currentTreeText: String = ""

    def flushNewPhaseTree(): Unit = {
      if (currentPhase != null) {
        buffer += PhaseWithTreeText(currentPhase, currentTreeText)
      }
      currentTreeText = ""
    }

    while (iterator.hasNext) {
      val msg = iterator.next()
      val text = msg.text
      text match {
        case Scala2TreePhaseOutputRegexp(phase) =>
          flushNewPhaseTree()
          currentPhase = phase
        case _ =>
          //tree always starts with "package", if the package is empty it's "package <empty>"
          if (currentPhase != null && text.startsWith("package")) {
            currentTreeText = text
          }
      }
    }

    flushNewPhaseTree()

    new CompilerTrees(buffer.toSeq)
  }


  //Scala 3 output example:
  //[[syntax trees at end of                    parser]] // /Users/user/../Example.scala <NEW_LINE> tree at multiliple lines
  //[[syntax trees at end of MegaPhase{dropOuterAccessors, checkNoSuperThis, flatten}]] // /Users/user/../Example.scala <NEW_LINE> tree at multiliple lines
  private val Scala3TreePhaseOutputWithTreeRegexp = """(?s)\[\[\s*syntax trees at end of\s+(.*?)]].*?\r?\n(.*)""".r
  private val Scala2TreePhaseOutputRegexp = """\[\[\s*syntax trees at end of\s+(.*?)]].*?""".r

  private def parseForScala3(messages: Seq[Client.ClientMsg]): CompilerTrees = {
    val phaseToTreeText = messages.map(_.text).collect {
      case Scala3TreePhaseOutputWithTreeRegexp(phaseText, treeText) =>
        PhaseWithTreeText(phaseText.trim, treeText.trim)
    }
    new CompilerTrees(phaseToTreeText)
  }
}