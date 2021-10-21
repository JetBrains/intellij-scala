package org.jetbrains.plugins.scala.lang.resolveSemanticDb

import org.jetbrains.kotlin.utils.fileUtils.FileUtilsKt

import java.nio.file.{Files, Path}
import scala.collection.mutable
import scala.meta.internal.semanticdb.Locator
import scala.reflect.io.Directory


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

case class SemanticDbStore(files: Seq[SDbFile]) {
  def serialized: String = {
    val b = new StringBuilder

    for (file <- files) {
      b ++= "Document "
      b ++= file.path
      b += '\n'

      for (ref <- file.references) {
        b ++= s"(${ref.position}..${ref.endPosition}) ${ref.symbol}"
        ref.targetPosition.foreach { targetPosition =>
          b ++= s" -> $targetPosition"
        }
        b += '\n'
      }

      b += '\n'
    }

    val resultText = b.result()

    // check that serialize and fromText work together
    assert(this == SemanticDbStore.fromText(resultText))

    resultText
  }
}

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

  def fromTextFile(path: Path): SemanticDbStore =
    fromText(Files.readString(path))

  def fromText(text: String): SemanticDbStore = {
    val lines = text.linesIterator
    val files = Seq.newBuilder[SDbFile]

    while (lines.hasNext) {
      val pathLine = lines.next()
      assert(pathLine.startsWith("Document "))
      val path = pathLine.stripPrefix("Document ")

      val refs =
        for (refLine <- lines.takeWhile(_.nonEmpty)) yield {
          refLine match {
            case RefFromLine(ref) => ref
            case s => throw new Exception("not a refline: " + s)
          }
        }

      files += SDbFile(path, refs.toSeq)
    }
    SemanticDbStore(files.result())
  }

  private object RefFromLine {
    private val pos = raw"(\d+):(\d+)"
    private val RefLineParts = raw"\($pos\.\.$pos\) (.+?)(?: -> $pos)?".r

    def unapply(s: String): Option[SDbRef] = {
      s match {
        case RefLineParts(startLine, startCol, endLine, endCol, symbol, targetLine, targetCol) =>
          val targetPosition =
            if (targetLine == null) None
            else Some(TextPos(targetLine.toInt, targetCol.toInt))
          Some(SDbRef(symbol, TextPos(startLine.toInt, startCol.toInt), TextPos(endLine.toInt, endCol.toInt), targetPosition))
        case _ => None
      }
    }
  }



  /* Code to convert semanticdb binary data to new text based format

  def main(args: Array[String]): Unit = {
    val main = Path.of("/home/tobi/workspace/intellij-scala/community/scala/scala-impl/testdata/lang/resolveSemanticDb/out")
    Files.list(main)
      .filter(Files.isDirectory(_))
      .forEach { path =>
        val store = SemanticDbStore.fromSemanticDbPath(path)
        Files.writeString(main.resolve(path.getFileName.toString + ".semdb"), store.serialized)
      }
  }
  */
}
