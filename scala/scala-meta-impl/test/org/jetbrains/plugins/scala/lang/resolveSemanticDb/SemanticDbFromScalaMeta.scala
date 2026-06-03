package org.jetbrains.plugins.scala.lang.resolveSemanticDb

import org.jetbrains.plugins.scala.lang.resolveSemanticDb.TextPos.fromZeroBased

import java.nio.file.Path
import scala.collection.mutable
import scala.meta.internal.semanticdb

object SemanticDbFromScalaMeta {
  def fromSemanticDbPath(path: Path): SemanticDbStore = {
    type UnfinishedRef = (String, TextPos, TextPos)
    val positionOfSymbols = mutable.Map.empty[String, TextPos]
    val unfinishedFiles = mutable.Map.empty[String, Seq[UnfinishedRef]]
    semanticdb.Locator(path) { (_, payload) =>
      for (doc <- payload.documents) {
        val refs = Seq.newBuilder[UnfinishedRef]
        for (occurrence <- doc.occurrences) {
          def start = textPosOfStart(occurrence.range.get)
          def end = textPosOfEnd(occurrence.range.get)
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
          SDbFile(path, refs.sorted)
      }
      .toSeq
      .sortBy(_.path)

    SemanticDbStore(files)
  }

  private def textPosOfStart(range: semanticdb.Range): TextPos =
    fromZeroBased(range.startLine, range.startCharacter)
  private def textPosOfEnd(range: semanticdb.Range): TextPos =
    fromZeroBased(range.endLine, range.endCharacter)
}