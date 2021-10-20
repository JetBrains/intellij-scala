package org.jetbrains.plugins.scala.lang.resolveSemanticDb

import java.nio.file.Path
import scala.collection.mutable
import scala.meta.internal.semanticdb.Locator


case class SDbRef(symbol: String, position: TextPos, endPosition: TextPos, targetPosition: Option[TextPos]) {
  def range: (TextPos, TextPos) = (position, endPosition)
  lazy val pointsToLocal: Boolean = symbol.matches(raw"local\d+")

  override def toString: String =
    s"$symbol($position..$endPosition) -> ${targetPosition.fold("<no position>")(_.toString)}"
}

case class SDbFile(path: String, references: Seq[SDbRef]) {
  def referencesAt(pos: TextPos, empty: Boolean): Seq[SDbRef] =
    references.filter(if (empty) _.range.is(pos) else _.range.contains(pos))
}

case class SemanticDbStore(files: Seq[SDbFile])

object SemanticDbStore {
  def fromSemanticDbPath(path: Path): SemanticDbStore = {
    type UnfinishedRef = (String, TextPos, TextPos)
    val positionOfSymbols = mutable.Map.empty[String, TextPos]
    val unfinishedFiles = mutable.Map.empty[String, Seq[UnfinishedRef]]
    Locator(path) { (_, payload) =>
      for (doc <- payload.documents) {
        val refs = Seq.newBuilder[UnfinishedRef]
        for (occurrence <- doc.occurrences) {
          def start = TextPos.ofStart(occurrence.range.get)
          def end = TextPos.ofEnd(occurrence.range.get)
          if (occurrence.role.isDefinition) positionOfSymbols += occurrence.symbol -> start
          else if (occurrence.role.isReference) refs += ((occurrence.symbol, start, end))
        }
        unfinishedFiles += doc.uri -> refs.result()
      }
    }

    val files = unfinishedFiles.iterator
      .map {
        case (path, unfinishedRefs) =>
          val refs =
            for ((symbol, start, end) <- unfinishedRefs)
              yield SDbRef(symbol, start, end, targetPosition = positionOfSymbols.get(symbol))
          SDbFile(path, refs)
      }
      .toSeq

    SemanticDbStore(files)
  }
}
