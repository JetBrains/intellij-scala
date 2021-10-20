package org.jetbrains.plugins.scala.lang.resolveSemanticDb

import java.nio.file.Path
import scala.meta.internal.semanticdb
import scala.meta.internal.semanticdb.{Locator, SymbolInformation, SymbolOccurrence}


class SDbFile(val path: String, _occurrences: => Seq[SDbOccurrence]) {
  lazy val occurrences: Seq[SDbOccurrence] = _occurrences

  def referencesAt(pos: TextPos, empty: Boolean): Seq[SDbOccurrence] =
    occurrences.filter(_.isReference).filter(if (empty) _.range.is(pos) else _.range.contains(pos))
}

class SDbSymbol(_file: => SDbFile, val info: SymbolInformation)(_definition: => SDbOccurrence) {
  def position: TextPos = definition.position

  lazy val file: SDbFile = _file
  lazy val definition: SDbOccurrence = _definition

  override def toString: String =
    s"def ${definition.toString}"
}

class SDbOccurrence(val info: SymbolOccurrence, _symbol: => Option[SDbSymbol]) {
  lazy val symbol: Option[SDbSymbol] = _symbol
  def range: semanticdb.Range = info.range.get
  def position: TextPos = TextPos(range.startLine, range.startCharacter)
  def isDefinition: Boolean = info.role.isDefinition
  def isReference: Boolean = info.role.isReference
  lazy val pointsToLocal: Boolean = info.symbol.matches(raw"local\d+")

  override def toString: String =s"${info.symbol}(${range.mkString})" + (
    if (isReference) s" -> ${symbol.fold("<no symbol>")(_.toString)}"
    else ""
    )
}

class SemanticDbStore(val files: Seq[SDbFile], val symbols: Map[String, SDbSymbol])

object SemanticDbStore {
  def apply(path: Path): SemanticDbStore = {
    lazy val store: SemanticDbStore = {
      var symbols = Map.empty[String, SDbSymbol]
      var definitions = Map.empty[String, SDbOccurrence]
      val files = Seq.newBuilder[SDbFile]
      Locator(path) { (_, payload) =>
        for (doc <- payload.documents) {
          lazy val file: SDbFile = {
            for (sym <- doc.symbols) {
              assert(!symbols.contains(sym.symbol))
              symbols += sym.symbol -> new SDbSymbol(file, sym)(definitions(sym.symbol))
            }

            val occurrences =
              for (occurrence <- doc.occurrences)
                yield new SDbOccurrence(occurrence, store.symbols.get(occurrence.symbol))

            for (occurrence <- occurrences if occurrence.isDefinition) {
              definitions += occurrence.info.symbol -> occurrence
            }

            new SDbFile(doc.uri, occurrences)
          }

          files += file
        }
      }

      new SemanticDbStore(files.result(), symbols)
    }

    store
  }
}
