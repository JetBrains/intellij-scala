package org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees

import org.jetbrains.jps.incremental.scala.{Client, MessageKind}
import org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees.CompilerTrees.PhaseWithTreeText

import scala.collection.mutable.ArrayBuffer

object TastyOutputParser {

  // Example:
  // line 1: **** pickled info of class MyClass
  // line 2: Header: Names 123 Trees 456 Positions 789 Attributes 012
  // line 3: **** end of pickled info of class MyClass
  //
  // Example start messages:
  // **** pickled info of class/trait/object/enum MyClass
  // **** pickled info of the top-level definitions in package example
  private val TastyStartPattern = """\*{2,} pickled info of (.+)""".r
  private val TastyEndPattern = """\*{2,} end of pickled info of (.+)""".r

  /**
   * Parses Tasty output from compiler messages.
   *
   * Tasty output comes with warning messages:
   *  - Warning: **** pickled info of class A (start marker)
   *  - Warning: Header: ... Names ... Trees ... Positions ... Attributes (intermediate output)
   *  - Warning: [possibly more intermediate output messages]
   *  - Warning: **** end of pickled info of class A (end marker)
   *
   * @note There can be multiple intermediate output messages between the start and end markers
   * @note Each source file can contain multiple outputs for every top-level class definition
   */
  def parse(messages: Seq[Client.ClientMsg]): Option[(Seq[Client.ClientMsg], Seq[PhaseWithTreeText])] = {
    val warnings = messages.filter(_.kind == MessageKind.Warning)
    parseInner(warnings)
  }

  private def parseInner(messages: Seq[Client.ClientMsg]): Option[(Seq[Client.ClientMsg], Seq[PhaseWithTreeText])] = {
    val capturedMessages = ArrayBuffer.empty[Client.ClientMsg]
    val phases = ArrayBuffer.empty[PhaseWithTreeText]

    var messageIdx = 0
    while (messageIdx < messages.length) {
      val currentMessage = messages(messageIdx)
      currentMessage.text.trim match {
        case TastyStartPattern(entityName) =>
          capturedMessages += currentMessage
          messageIdx += 1

          val intermediateContent = ArrayBuffer.empty[String]
          var foundEnd = false
          while (messageIdx < messages.length && !foundEnd) {
            val nextMessage = messages(messageIdx)

            capturedMessages += nextMessage
            messageIdx += 1

            nextMessage.text.trim match {
              case TastyEndPattern(endEntityName) if endEntityName == entityName =>
                foundEnd = true
              case content =>
                intermediateContent += content
            }
          }

          if (foundEnd) {
            val tastyContent = intermediateContent.mkString("\n")
            phases += PhaseWithTreeText(s"Tasty ($entityName)", tastyContent, CompilerTrees.PhaseKind.TastyOutput)
          }

        case _ =>
          messageIdx += 1
      }
    }

    if (capturedMessages.nonEmpty)
      Some((capturedMessages.toSeq, phases.toSeq))
    else
      None
  }
}
