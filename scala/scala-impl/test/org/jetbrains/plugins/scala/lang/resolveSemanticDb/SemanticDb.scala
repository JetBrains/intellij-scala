package org.jetbrains.plugins.scala.lang.resolveSemanticDb

import java.nio.file.{Files, Path}
import scala.collection.mutable


case class SDbRef(symbol: String, position: TextPos, endPosition: TextPos, targetPosition: Option[TextPos]) {
  def range: (TextPos, TextPos) = (position, endPosition)
  lazy val pointsToLocal: Boolean = symbol.matches(raw"local\d+")
  lazy val isTypeParamRef: Boolean = symbol.endsWith("]")

  override def toString: String =
    s"$symbol($position..$endPosition) -> ${targetPosition.fold("<no position>")(_.toString)}"
}

object SDbRef {
  implicit val ordering: Ordering[SDbRef] =
    Ordering.by[SDbRef, TextPos](_.position)
      .orElseBy(_.endPosition)
      .orElseBy(_.symbol)
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
  /**
   * Reads a SemanticDbStore from a text file.
   * For the creation of these text files see [[SemanticDbFromScalaMeta.fromSemanticDbPath]].
   * It is created in [[AfterUpdateDottyVersionScript]].
   */
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
