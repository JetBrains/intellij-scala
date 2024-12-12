package org.jetbrains.plugins.scala.inferAst

import org.jetbrains.plugins.scala.extensions.ObjectExt

import scala.collection.mutable

trait ElementAstAction
object ElementAstAction {
  case class Token(token: String) extends ElementAstAction
  case class SubElement(element: String) extends ElementAstAction
  case class Error(error: String) extends ElementAstAction
  case class Call(analysisItem: AnalysisItem) extends ElementAstAction
  case object Empty extends ElementAstAction
}


object ElementAst {
  def from(input: AstAutomaton[AstAction]): (AstAutomaton[ElementAstAction], Map[String, AstAutomaton[ElementAstAction]]) = {

    trait ExitResult
    object ExitResult {
      case class Done(elementType: String, inner: AstAutomaton[ElementAstAction]#Node) extends ExitResult
      case class Collapsed(tokenType: String) extends ExitResult
      case class Dropped(dropped: AstAutomaton[ElementAstAction]#Node) extends ExitResult
      case object RolledBack extends ExitResult
    }
    class Result(val resultAutomaton: AstAutomaton[ElementAstAction])(val exits: Map[input.Node, ExitResult])
    val results = mutable.Map.empty[input.Node, Result]

    def resultFor(inputNode: input.Node): Result = results.getOrElseUpdate(inputNode, {
      val marker = inputNode.action match {
        case AstAction.Mark(foundMarker) => Some(foundMarker)
        case _ if inputNode.isStart => None
        case _ => throw new Exception("Expected start or mark action, got " + inputNode.action)
      }
      val resultA = new AstAutomaton[ElementAstAction]
      val resultExits = Map.newBuilder[input.Node, ExitResult]
      val nodeMapping = mutable.Map.empty[input.Node, Seq[resultA.Node]]

      def process(node: input.Node): Seq[resultA.Node] = {
        nodeMapping.get(node) match {
          case Some(result) => return result
          case None =>
        }
        def processNexts(): Seq[resultA.Node] =
          node.to.iterator.flatMap(process).toSeq

        node.action match {
          case AstAction.Empty => processNexts()
          case AstAction.Token(token) =>
            val newNode = new resultA.Node(ElementAstAction.Token(token))
            nodeMapping += node -> Seq(newNode)
            newNode ~> processNexts()
            Seq(newNode)
          case AstAction.Call(item) =>
            val newNode = new resultA.Node(ElementAstAction.Call(item))
            nodeMapping += node -> Seq(newNode)
            newNode ~> processNexts()
            Seq(newNode)
          case AstAction.Error(error) =>
            val newNode = new resultA.Node(ElementAstAction.Error(error))
            nodeMapping += node -> Seq(newNode)
            newNode ~> processNexts()
            Seq(newNode)
          case AstAction.Mark(_) if node eq inputNode =>
            processNexts()
          case AstAction.Mark(foundMarker) =>
            val inner = resultFor(node)
            val (inNodes, connect) = inner.exits.iterator.map {
              case (exit, exitResult) =>
                lazy val nexts =
                  exit.to.iterator.flatMap(process).toSeq
                def connectToNexts(out: resultA.Node): () => Unit =
                  () => out ~> nexts
                exitResult match {
                  case ExitResult.Done(elementType, _) =>
                    val actualNode = new resultA.Node(ElementAstAction.SubElement(elementType))
                    actualNode -> connectToNexts(actualNode)
                  case ExitResult.Collapsed(tokenType) =>
                    val actualNode = new resultA.Node(ElementAstAction.Token(tokenType))
                    actualNode -> connectToNexts(actualNode)
                  case ExitResult.Dropped(dropped) =>
                    val resultStarts = inner.resultAutomaton.starts
                    val droppedNodes = inner.resultAutomaton.nodesBetween(resultStarts, Seq(dropped.asInstanceOf[inner.resultAutomaton.Node]))
                    val mapping = resultA.copyFrom(droppedNodes)
                    val resultStartsInHere = resultStarts.flatMap(mapping.get)
                    val start =
                      if (resultStartsInHere.size == 1) {
                        resultStartsInHere.head
                      } else {
                        val empty = new resultA.Node(ElementAstAction.Empty)
                        empty ~> resultStartsInHere
                        empty
                      }
                    start -> connectToNexts(mapping(dropped))
                  case ExitResult.RolledBack =>
                    val empty = new resultA.Node(ElementAstAction.Empty)
                    empty -> connectToNexts(empty)
                }
            }.toSeq.unzip

            nodeMapping += node -> inNodes
            connect.foreach(f => f())
            inNodes
          case AstAction.Done(foundMarker, elementType) =>
            val doneNode = new resultA.Node(ElementAstAction.Empty)
            nodeMapping += node -> Nil
            if (marker.contains(foundMarker)) {
              doneNode.markExit()
              resultExits += node -> ExitResult.Done(elementType, doneNode)
            }
            Seq(doneNode)
          case AstAction.Collapse(foundMarker, tokenType) =>
            nodeMapping += node -> Nil
            if (marker.contains(foundMarker)) {
              resultExits += node -> ExitResult.Collapsed(tokenType)
            }
            Nil
          case AstAction.Drop(foundMarker) =>
            val dropNode = new resultA.Node(ElementAstAction.Empty)
            nodeMapping += node -> Nil
            if (marker.contains(foundMarker)) {
              resultExits += node -> ExitResult.Dropped(dropNode)
            }
            Seq(dropNode)
          case AstAction.Rollback(foundMarker) =>
            nodeMapping += node -> Nil
            if (marker.contains(foundMarker)) {
              resultExits += node -> ExitResult.RolledBack
            }
            Nil
          case AstAction.Precede(_, _) =>
            ???
        }
      }

      process(inputNode).foreach(_.markStart())
      new Result(resultA)(resultExits.result())
    })


    val mainResult = AstAutomaton.squash(input.starts.iterator.map(resultFor).map(_.resultAutomaton))
    var inners = Map.empty[String, AstAutomaton[ElementAstAction]]
    results.valuesIterator.foreach {
      result =>
        result.exits.valuesIterator.foreach {
          case ExitResult.Done(elementType, inner) =>
            val tillHere = inner.tillHereAsAutomata
            inners.get(elementType).foreach(tillHere.merge)
            inners += elementType -> tillHere
          case _ =>
            // nothing to do
        }
    }

    def removeEmpty(automata: AstAutomaton[ElementAstAction]): Unit =
      automata.removeAllInPlace(connectAdjacent = true)(_.action == ElementAstAction.Empty)

    removeEmpty(mainResult)
    inners.valuesIterator.foreach(removeEmpty)

    mainResult -> inners
  }
}
