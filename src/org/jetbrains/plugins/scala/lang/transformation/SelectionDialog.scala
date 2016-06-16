package org.jetbrains.plugins.scala.lang.transformation

import java.awt.Dimension
import javax.swing._
import javax.swing.tree.{DefaultTreeCellRenderer, TreeNode}

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.SystemInfo
import com.intellij.profile.codeInspection.ui.table.ThreeStateCheckBoxRenderer
import com.intellij.ui.treeStructure.treetable.{ListTreeTableModel, TreeColumnInfo, TreeTable}
import com.intellij.util.ui.ColumnInfo
import org.jdesktop.swingx.treetable.DefaultMutableTreeTableNode
import org.jetbrains.plugins.scala.lang.transformation.annotations._
import org.jetbrains.plugins.scala.lang.transformation.calls._
import org.jetbrains.plugins.scala.lang.transformation.functions.{ExpandEtaExpansion, ExpandPlaceholderSyntax, MakeEtaExpansionExplicit}
import org.jetbrains.plugins.scala.lang.transformation.general._
import org.jetbrains.plugins.scala.lang.transformation.implicits._
import org.jetbrains.plugins.scala.lang.transformation.references._
import org.jetbrains.plugins.scala.lang.transformation.types._

class SelectionDialog {
  private val RootGroup = Group("root",
    Group("Method invocations",
      Entry("Expand \"apply\" call", ExpandApplyCall),
      Entry("Expand \"update\" call", ExpandUpdateCall),
      Entry("Expand unary call", ExpandUpdateCall),
      Entry("Expand property setter call", ExpandSetterCall),
      Entry("Expand assignment call", ExpandAssignmentCall),
      Entry("Expand dynamic call", ExpandDynamicCall),
      Entry("Canonize infix call", CanonizeInfixCall),
      Entry("Canonize postfix call", CanonizePostifxCall),
      Entry("Canonize arity-0 call", CanonizeZeroArityCall),
      Entry("Canonize block argument", CanonizeBlockArgument),
      Entry("Expand auto-tupling", ExpandAutoTupling),
      Entry("Expand vararg argument", ExpandVarargArgument, enabled = false),
      Entry("Inscribe default arguments", enabled = false),
      Entry("Expand \"==\" to \"equals\" call")
    ),
    Group("Type annotations",
      Entry("Value definition", AddTypeToValueDefinition),
      Entry("Variable definition", AddTypeToVariableDefinition),
      Entry("Method definition", AddTypeToMethodDefinition),
      Entry("Function parameter", AddTypeToFunctionParameter),
      Entry("Underscore parameter", AddTypeToUnderscoreParameter),
      Entry("Reference pattern", AddTypeToReferencePattern),
      Entry("Type parameters")
    ),
    Group("Types",
      Entry("Expand function type", ExpandFunctionType),
      Entry("Expand tuple type", ExpandTupleType),
      Entry("Expand type alias"),
      Entry("Expand context bound"),
      Entry("Expand view bound"),
      Entry("Substitute AnyRef")
    ),
    Group("Implicits",
      Entry("Expand implicit conversion", ExpandImplicitConversion),
      Entry("Inscribe implicit parameters", InscribeImplicitParameters),
      Entry("Expand conversion to String"),
      Entry("Expand boxing"),
      Entry("Expand unboxing")
    ),
    Group("Functions",
      Entry("Expand placeholder syntax", ExpandPlaceholderSyntax),
      Entry("Expand eta expansion", ExpandEtaExpansion),
      Entry("Make eta-expansion explicit", MakeEtaExpansionExplicit),
      Entry("Expand single abstract methods", enabled = false),
      Entry("Expand function instantiation", enabled = false)
    ),
    Group("Expressions",
      Entry("Expand for comprehensions", ExpandForComprehension),
      Entry("Expand string interpolation", ExpandStringInterpolation),
      Entry("Expand tuple instantiation", ExpandTupleInstantiation)
    ),
    Group("Declarations",
      Entry("Expand procedure syntax"),
      Entry("Make method return expressions explicit"),
      Entry("Add explicit \"override\" modifier"),
      Entry("Replace underscore section with default value"),
      Entry("Expand property declaration", enabled = false),
      Entry("Expand property definition", enabled = false),
      Entry("Convert implicit class to class and function")
    ),
    Group("References",
      Entry("Expand wildcard import"),
      Entry("Fully qualify import expression"),
      Entry("Partially qualify simple reference", PartiallyQualifySimpleReference),
      Entry("Fully qualify reference", enabled = false)
    ),
    Group("General",
      Entry("Append semicolon", AppendSemicolon, enabled = false),
      Entry("Inscribe explicit braces", enabled = false),
      Entry("Enforce parentheses in constructor invocation"),
      Entry("Convert parentheses to braces in for comprehensions"),
      Entry("Expand macro", enabled = false)
    )
  )

  def show(title: String): Option[Set[Transformer]] = {
    val dialog = new MyDialog(RootGroup)
    dialog.setTitle(title)

    if (dialog.showAndGet()) Some(RootGroup.transformers.filterNot(_ == null).toSet) else None
  }

  private class MyDialog(root: Group) extends DialogWrapper(false) {
    init()

    protected def createCenterPanel = {
      val rightColumn = new ColumnInfo[Node, java.lang.Boolean]("Enabled") {
        override def getColumnClass = classOf[Boolean]

        override def isCellEditable(item: Node) = true

        override def valueOf(node: Node) = node.value.map(Boolean.box).orNull

        override def setValue(node: Node, value: java.lang.Boolean) {
          val toggle = node.value.forall(!_)
          node.value = Some(toggle)
        }
      }

      val model = new ListTreeTableModel(root, Array(new TreeColumnInfo("Transformation"), rightColumn)) {
        override def setValueAt(aValue: Any, node: Any, column: Int) {
          super.setValueAt(aValue, node, column)

          nodeChanged(node.asInstanceOf[TreeNode])
        }
      }

      val table = new TreeTable(model)
      table.setTableHeader(null)
      table.setRootVisible(false)

      root.nodes.indices.reverse.foreach(table.getTree.expandRow)

      val treeCellRenderer = new DefaultTreeCellRenderer()
      treeCellRenderer.setOpenIcon(null)
      treeCellRenderer.setClosedIcon(null)
      treeCellRenderer.setLeafIcon(null)
      table.setTreeCellRenderer(treeCellRenderer)

      val cellRenderer = new ThreeStateCheckBoxRenderer()
      table.getColumnModel.getColumn(1).setCellRenderer(cellRenderer)
      table.getColumnModel.getColumn(1).setCellEditor(cellRenderer)

      table.getColumnModel.getColumn(1).setMaxWidth(20 + padding)

      val result = new JScrollPane(table)
      result.setPreferredSize(new Dimension(350, 450))

      result
    }
  }

  private def padding = if (SystemInfo.isMac) 10 else 0
}

private abstract class Node(name: String) extends DefaultMutableTreeTableNode(name) {
  var value: Option[Boolean]

  def transformers: Seq[Transformer]
}

private case class Group(name: String, nodes: Node*) extends Node(name) {
  nodes.foreach(add)

  def value =
    if (nodes.forall(_.value.contains(true))) Some(true)
    else if (nodes.forall(_.value.contains(false))) Some(false)
    else None

  def value_=(b: Option[Boolean]) {
    nodes.foreach(_.value = b)
  }

  def transformers = nodes.flatMap(_.transformers)
}

// TODO remove the default argument when all transformers are implemented
private case class Entry(name: String, private val transformer: Transformer = null, private val enabled: Boolean = true) extends Node(name) {
  var value: Option[Boolean] = Some(enabled)

  def transformers = if (value.contains(true)) Seq(transformer) else Seq.empty
}
