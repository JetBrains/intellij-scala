package org.jetbrains.plugins.scala.traceLogViewer

import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns
import com.intellij.util.ui.ColumnInfo
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.traceLogViewer.TraceLogModel.{Columns, Node}
import org.jetbrains.plugins.scala.traceLogger.{Data, TraceLogReader}
import org.jetbrains.plugins.scala.traceLogger.TraceLogReader.EnclosingResult
import org.jetbrains.plugins.scala.traceLogger.protocol.TraceLoggerEntry

import java.util
import javax.swing.table.TableCellRenderer
import javax.swing.tree.TreeNode
import scala.collection.immutable.ArraySeq
import scala.collection.mutable
import scala.jdk.CollectionConverters.IteratorHasAsJava

class TraceLogModel(root: Node) extends ListTreeTableModelOnColumns(root, Columns.toArray)

object TraceLogModel {
  def createFromLines(lines: Iterator[String]): TraceLogModel = {
    val roots = NodesReader.readLines(lines)
    val root = new EnclosingNode("Log Roots", Seq.empty, roots)
    new TraceLogModel(root)
  }

  private val Columns: Array[ColumnInfo[Node, _]] = Array(
    new ColumnInfo[Node, Node]("Message") {
      override def valueOf(item: Node): Node = item
      override def getRenderer(item: Node): TableCellRenderer = new TraceLogTreeCellRenderer
    },
    new ColumnInfo[Node, String]("Values") {
      override def valueOf(item: Node): String = item.values
        .map { case (name, data) => s"$name: $data" }
        .mkString(", ")
    }
  )

  abstract class Node(val msg: String, val values: Seq[(String, Data)]) extends TreeNode {
    @Nullable
    var parent: Node = _

    lazy val depth: Int = Option(parent).fold(-1)(_.depth + 1)

    override def getParent: TreeNode = parent
  }

  final class MsgNode(_msg: String, _values: Seq[(String, Data)]) extends Node(_msg, _values) {
    override def getChildAt(childIndex: Int): TreeNode = throw new IndexOutOfBoundsException("MsgNodes don't have any children")
    override def getChildCount: Int = 0
    override def getIndex(node: TreeNode): Int = -1
    override def getAllowsChildren: Boolean = false
    override def isLeaf: Boolean = true
    override def children(): util.Enumeration[_ <: TreeNode] = util.Collections.emptyEnumeration()
  }

  final class EnclosingNode(_msg: String, _values: Seq[(String, Data)], val childrenSeq: ArraySeq[Node]) extends Node(_msg, _values) {
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

    override protected def createMsgNode(msg: TraceLoggerEntry.Msg): Node =
      new MsgNode(msg.msg, msg.values)

    override protected def createEnclosingNode(start: TraceLoggerEntry.Start, inners: ArraySeq[Node], result: EnclosingResult): Node = {
      val enclosing = new EnclosingNode(start.msg, start.values, inners)
      enclosing
    }
  }
}