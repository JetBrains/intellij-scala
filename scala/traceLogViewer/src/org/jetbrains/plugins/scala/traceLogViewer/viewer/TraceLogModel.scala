package org.jetbrains.plugins.scala.traceLogViewer.viewer

import com.intellij.execution.filters.{CompositeFilter, ExceptionFilters}
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.TreeTableSpeedSearch
import com.intellij.ui.treeStructure.treetable.{ListTreeTableModelOnColumns, TreeTable}
import com.intellij.util.ui.ColumnInfo
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions.{NullSafe, ToNullSafe}
import org.jetbrains.plugins.scala.traceLogViewer.ClickableColumn
import org.jetbrains.plugins.scala.traceLogViewer.viewer.TraceLogModel._
import org.jetbrains.plugins.scala.traceLogger.TraceLogReader.EnclosingResult
import org.jetbrains.plugins.scala.traceLogger.protocol.{StackTraceEntry, TraceLoggerEntry}
import org.jetbrains.plugins.scala.traceLogger.{Data, TraceLogReader}

import java.awt.event.MouseEvent
import java.util
import javax.swing.table.TableCellRenderer
import javax.swing.tree.{TreeNode, TreePath}
import scala.collection.immutable.ArraySeq
import scala.collection.mutable
import scala.jdk.CollectionConverters.IteratorHasAsJava

class TraceLogModel(root: Node) extends ListTreeTableModelOnColumns(root, Columns.toArray) {
  def registerSpeedSearch(table: TreeTable): Unit = {
    new TreeTableSpeedSearch(table) {
      override def getElementText(element: Any): Data = {
        val path = element.asInstanceOf[TreePath]
        val node = path.getLastPathComponent.asInstanceOf[Node]
        s"${node.stackTrace.head.toStackTraceElement} ${node.msg} ${node.values.mkString(", ")}"
      }
    }.setCanExpand(true)
  }
}

//noinspection ScalaExtractStringToBundle
object TraceLogModel {
  def createFromLines(lines: Iterator[String]): TraceLogModel = {
    val roots = NodesReader.readLines(lines)
    val root = new EnclosingNode("Log Roots", Seq.empty, Nil, roots)
    new TraceLogModel(root)
  }

  private val Columns: Array[ColumnInfo[Node, _]] = Array(
    new ColumnInfo[Node, Node]("Function") with ClickableColumn[Node] {
      override def valueOf(item: Node): Node = item
      override def getRenderer(item: Node): TableCellRenderer = new TraceLogFunctionCellRenderer
      override def onClick(e: MouseEvent, item: Node): Unit =
        if (e.getClickCount >= 2) {
          gotoStackTraceEntry(item.stackTrace.head)
        }
    },
    new ColumnInfo[Node, Node]("Values") {
      override def valueOf(item: Node): Node = item
      override def getRenderer(item: Node): TableCellRenderer = new TraceLogValueCellRenderer
    },
    new ColumnInfo[Node, String]("Message") {
      override def valueOf(item: Node): String = item.msg
    },
  )

  private def gotoStackTraceEntry(entry: StackTraceEntry): Unit = {
    val goto = ProjectManager.getInstance()
      .getOpenProjects.iterator
      .flatMap { project =>
        val filter = new CompositeFilter(project, ExceptionFilters.getFilters(GlobalSearchScope.allScope(project)))
        val line = entry.toStackTraceElement.toString
        NullSafe(filter.applyFilter(line, line.length))
          .map(_.getFirstHyperlinkInfo)
          .map(_ -> project)
          .toOption
      }
      .nextOption()
    goto.foreach { case (link, project) => link.navigate(project) }
  }

  abstract class Node(val msg: String, val values: Seq[(String, Data)], val stackTrace: List[StackTraceEntry])
    extends TreeNode
  {
    @Nullable
    protected[TraceLogModel] var parent: Node = _

    lazy val depth: Int = Option(parent).fold(-1)(_.depth + 1)

    final def newStackTrace: List[StackTraceEntry] =
      stackTrace.dropRight(parentStackTrace.length)
    final def parentStackTrace: List[StackTraceEntry] =
      parent.nullSafe.fold(List.empty[StackTraceEntry])(_.stackTrace)

    @Nullable
    override def getParent: TreeNode = parent
  }

  final class MsgNode(_msg: String,
                      _values: Seq[(String, Data)],
                      _stackTrace: List[StackTraceEntry])
      extends Node(_msg, _values, _stackTrace)
  {
    override def getChildAt(childIndex: Int): TreeNode = throw new IndexOutOfBoundsException("MsgNodes don't have any children")
    override def getChildCount: Int = 0
    override def getIndex(node: TreeNode): Int = -1
    override def getAllowsChildren: Boolean = false
    override def isLeaf: Boolean = true
    override def children(): util.Enumeration[_ <: TreeNode] = util.Collections.emptyEnumeration()
  }

  final class EnclosingNode(_msg: String,
                            _values: Seq[(String, Data)],
                            _stackTrace: List[StackTraceEntry],
                            val childrenSeq: ArraySeq[Node])
    extends Node(_msg, _values, _stackTrace)
  {
    childrenSeq.foreach {
      child =>
        assert(child.parent == null)
        child.parent = this
    }

    override def getChildAt(childIndex: Int): TreeNode = childrenSeq(childIndex)
    override def getChildCount: Int = childrenSeq.size
    override def getIndex(node: TreeNode): Int = childrenSeq.indexOf(node)
    override def getAllowsChildren: Boolean = true
    override def isLeaf: Boolean = childrenSeq.isEmpty
    override def children(): util.Enumeration[_ <: TreeNode] = childrenSeq.iterator.asJavaEnumeration
  }

  private object NodesReader extends TraceLogReader {
    override type Node = TraceLogModel.Node
    override type NodeSeq = ArraySeq[Node]

    protected def newNodeSeqBuilder(): mutable.Builder[Node, NodeSeq] =
      ArraySeq.newBuilder

    override protected def createMsgNode(msg: TraceLoggerEntry.Msg, stackTrace: List[StackTraceEntry]): Node =
      new MsgNode(msg.msg, msg.values, stackTrace)

    override protected def createEnclosingNode(start: TraceLoggerEntry.Start,
                                               inners: ArraySeq[Node],
                                               result: EnclosingResult,
                                               stackTrace: List[StackTraceEntry]): Node =
      new EnclosingNode(start.msg, start.values, stackTrace, inners)
  }
}