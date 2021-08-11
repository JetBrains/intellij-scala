package org.jetbrains.plugins.scala.traceLogViewer.selection

import com.intellij.util.ui.{ColumnInfo, ListTableModel}
import org.jetbrains.plugins.scala.traceLogger.TraceLogger

import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{Files, Path}
import java.time.format.DateTimeFormatter
import java.time.{Duration, Instant, ZoneId}
import java.util.Comparator
import scala.jdk.CollectionConverters.*
import scala.jdk.StreamConverters.StreamHasToScala
import scala.util.Try


private object TraceLogSelectionModel extends ListTableModel[Entry](Entry.nameColumn, Entry.dateColumn) {
  final def refresh(): Option[Path] = {
    val prevItems = getItems.asScala
    val items = listEntries()
    setItems(items.asJava)

    val newItems = items.toSet -- prevItems

    if (newItems.size == 1) newItems.headOption.map(_.path) else None
  }

  private def listEntries(): Seq[Entry] = {
    val paths = Try(Files.list(TraceLogger.loggerOutputPath).toScala(Seq))
      .getOrElse(Seq.empty)
    for (path <- paths) yield {
      val attr = Try(Files.readAttributes(path, classOf[BasicFileAttributes])).toOption
      Entry(
        path.getFileName.toString,
        path,
        Instant.ofEpochMilli(attr.fold(0L)(_.lastModifiedTime().toMillis))
      )
    }
  }
}

private case class Entry(name: String, path: Path, date: Instant)
//noinspection ScalaExtractStringToBundle
private object Entry {
  def nameColumn: ColumnInfo[Entry, String] = new ColumnInfo[Entry, String]("Log") {
    override def valueOf(item: Entry): String = item.name

    override def getComparator: Comparator[Entry] = Comparator.comparing(_.name)
  }

  def dateColumn: ColumnInfo[Entry, String] = new ColumnInfo[Entry, String]("Created") {
    private val formatter =
      DateTimeFormatter.ofPattern("hh:mm  (()) (dd.MM.yyyy)")
        .withZone(ZoneId.systemDefault())

    override def valueOf(item: Entry): String = {
      val date = item.date
      val daysAgo = Duration.between(date, Instant.now()).toDays
      val ago = daysAgo match {
        case 0 => "today"
        case 1 => "yesterday"
        case _ => s""
      }

      formatter.format(item.date).replace("(())", ago)
    }

    override def getComparator: Comparator[Entry] = Comparator.comparing(_.date)
  }
}