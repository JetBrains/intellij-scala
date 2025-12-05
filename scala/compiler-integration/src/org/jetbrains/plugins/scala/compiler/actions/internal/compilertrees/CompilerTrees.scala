package org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees

import org.jetbrains.jps.incremental.scala.{Client, MessageKind}
import org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees.CompilerTrees.{PhaseKind, PhaseWithTreeText}
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel

import scala.collection.mutable.ArrayBuffer

final class CompilerTrees(
  val phasesTrees: Seq[PhaseWithTreeText]
) {
  lazy val allPhasesTextConcatenated: String = phasesTrees
    .filter(_.kind == PhaseKind.Regular)
    .map { pt =>
      s"""// Phase: ${pt.phase}
         |${pt.phaseText}""".stripMargin
    }
    .mkString("\n\n")
}

object CompilerTrees {

  sealed trait PhaseKind
  object PhaseKind {
    case object Regular extends PhaseKind
    case object TastyOutput extends PhaseKind
    case object UncapturedOutput extends PhaseKind
  }

  case class PhaseWithTreeText(
    phase: String,
    phaseText: String,
    kind: PhaseKind = PhaseKind.Regular
  )

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
   * In Scala 2 compiler tree messages are printed as warnings, without a pointer to file position.<br>
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
    val capturedWithPhases: Seq[(Client.ClientMsg, PhaseWithTreeText)] =
      messages.collect {
        case msg @ Client.ClientMsg(_, Scala3TreePhaseOutputWithTreeRegexp(phaseText, treeText), _, _, _, _, _) =>
          (msg, PhaseWithTreeText(phaseText.trim, treeText.trim))
      }

    // Tasty output comes with 3 warning messages:
    //     Warning: **** pickled info of class A
    //     Warning: Header: ... Names ... Trees ... Positions ... Attributes
    //     Warning: **** end of pickled info of class A
    val tastyOutputMessagesWithPhases: Option[(Seq[Client.ClientMsg], Seq[PhaseWithTreeText])] =
      TastyOutputParser.parse(messages)

    val capturedMessages = capturedWithPhases.map(_._1).toSet ++
      tastyOutputMessagesWithPhases.map(_._1).getOrElse(Seq.empty)
    val phaseToTreeText = capturedWithPhases.map(_._2) ++
      tastyOutputMessagesWithPhases.map(_._2).getOrElse(Seq.empty)

    val uncapturedMessages = messages.filterNot(capturedMessages.contains)

    // TODO: Currently it's expected that all the phases trees have a syntax similar to Scala
    //  But it's not true to all the uncaptured output (Warning/Info/Errors)
    //  Don't apply Scala syntax in the editor for the uncaptured output (see CompilerTreesDialog)
    val syntheticOutputPhases: Seq[PhaseWithTreeText] =
      buildSyntheticPhasesForUncapturedOutput(uncapturedMessages)

    new CompilerTrees(phaseToTreeText ++ syntheticOutputPhases)
  }

  private def buildSyntheticPhasesForUncapturedOutput(uncapturedMessages: Seq[Client.ClientMsg]): Seq[PhaseWithTreeText] = {
    val messagesByKind = uncapturedMessages
      .groupBy(_.kind)
      .toSeq
      .sortBy(_._1.toString)

    messagesByKind.flatMap { case (kind, messages) =>
      val text = messages.map(_.text).mkString("\n")
      if (text.nonEmpty)
        // Example: "== WARNING Output =="
        Some(PhaseWithTreeText(s"== ${kind.toString.toUpperCase} Output ==", text, PhaseKind.UncapturedOutput))
      else
        None
    }
  }
}