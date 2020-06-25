package org.jetbrains.plugins.scala.lang.transformation

import java.awt.Dimension
import java.lang

import javax.swing._
import javax.swing.tree.{DefaultTreeCellRenderer, TreeNode}
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.SystemInfo
import com.intellij.profile.codeInspection.ui.table.ThreeStateCheckBoxRenderer
import com.intellij.ui.treeStructure.treetable.{ListTreeTableModel, TreeColumnInfo, TreeTable}
import com.intellij.util.ui.ColumnInfo
import org.jdesktop.swingx.treetable.DefaultMutableTreeTableNode
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.transformation.annotations._
import org.jetbrains.plugins.scala.lang.transformation.calls._
import org.jetbrains.plugins.scala.lang.transformation.conversions.MakeBoxingExplicit
import org.jetbrains.plugins.scala.lang.transformation.declarations.{ExpandProcedureSyntax, MakeResultExpressionExplicit}
import org.jetbrains.plugins.scala.lang.transformation.functions.{ExpandEtaExpansion, ExpandPlaceholderSyntax, MakeEtaExpansionExplicit}
import org.jetbrains.plugins.scala.lang.transformation.general._
import org.jetbrains.plugins.scala.lang.transformation.implicits._
import org.jetbrains.plugins.scala.lang.transformation.references._
import org.jetbrains.plugins.scala.lang.transformation.types._

class SelectionDialog {
  private val RootGroup = Group("root",
    Group("Method invocations",
      Entry("Expand \"apply\" call", new ExpandApplyCall()),
      Entry("Expand \"update\" call", new ExpandUpdateCall()),
      Entry("Expand unary call", new ExpandUpdateCall()),
      Entry("Expand property setter call", new ExpandSetterCall()),
      Entry("Expand assignment call", new ExpandAssignmentCall()),
      Entry("Expand dynamic call", new ExpandDynamicCall()),
      Entry("Canonize infix call", new CanonizeInfixCall()),
      Entry("Canonize postfix call", new CanonizePostifxCall()),
      Entry("Canonize arity-0 call", new CanonizeZeroArityCall()),
      Entry("Canonize block argument", new CanonizeBlockArgument()),
      Entry("Expand auto-tupling", new ExpandAutoTupling()),
      Entry("Expand vararg argument", new ExpandVarargArgument(), enabled = false),
      Entry("Inscribe default arguments", enabled = false),
      Entry("Expand \"==\" to \"equals\" call")
    ),
    Group("Type annotations",
      Entry("Value definition", new AddTypeToValueDefinition()),
      Entry("Variable definition", new AddTypeToVariableDefinition()),
      Entry("Method definition", new AddTypeToMethodDefinition()),
      Entry("Function parameter", new AddTypeToFunctionParameter()),
      Entry("Underscore parameter", new AddTypeToUnderscoreParameter()),
      Entry("Reference pattern", new AddTypeToReferencePattern()),
      Entry("Type parameters")
    ),
    Group("Types",
      Entry("Expand function type", new ExpandFunctionType()),
      Entry("Expand tuple type", new ExpandTupleType()),
      Entry("Expand type alias"),
      Entry("Expand context bound"),
      Entry("Expand view bound"),
      Entry("Substitute AnyRef")
    ),
    Group("Implicits",
      Entry("Expand implicit conversion", new ExpandImplicitConversion()),
      Entry("Inscribe implicit parameters", new InscribeImplicitParameters())
    ),
    Group("Functions",
      Entry("Expand placeholder syntax", new ExpandPlaceholderSyntax()),
      Entry("Expand eta expansion", new ExpandEtaExpansion()),
      Entry("Make eta-expansion explicit", new MakeEtaExpansionExplicit()),
      Entry("Expand single abstract methods", enabled = false),
      Entry("Expand function instantiation", enabled = false)
    ),
    Group("Expressions",
      Entry("Expand for comprehensions", new ExpandForComprehension()),
      Entry("Expand string interpolation", new ExpandStringInterpolation()),
      Entry("Expand tuple instantiation", new ExpandTupleInstantiation())
    ),
    Group("Declarations",
      Entry("Expand procedure syntax", new ExpandProcedureSyntax()),
      Entry("Make method return expressions explicit", new MakeResultExpressionExplicit()),
      Entry("Add explicit \"override\" modifier"),
      Entry("Replace underscore section with default value"),
      Entry("Expand property declaration", enabled = false),
      Entry("Expand property definition", enabled = false),
      Entry("Convert implicit class to class and function")
    ),
    Group("References",
      Entry("Expand wildcard import"),
      Entry("Fully qualify import expression"),
      Entry("Partially qualify simple reference", new PartiallyQualifySimpleReference()),
      Entry("Fully qualify reference", enabled = false)
    ),
    Group("General",
      Entry("Append semicolon", new AppendSemicolon(), enabled = false),
      Entry("Inscribe explicit braces", enabled = false),
      Entry("Enforce parentheses in constructor invocation"),
      Entry("Convert parentheses to braces in for comprehensions"),
      Entry("Expand macro", enabled = false)
    )
  )

  def show(@Nls title: String): Option[Set[Transformer]] = {
    val dialog = new MyDialog(RootGroup)
    dialog.setTitle(title)

    if (dialog.showAndGet()) Some(RootGroup.transformers.filterNot(_ == null).toSet) else None
  }

  private class MyDialog(root: Group) extends DialogWrapper(false) {
    init()

    override protected def createCenterPanel: JComponent = {
      val rightColumn: ColumnInfo[Node, lang.Boolean] = new ColumnInfo[Node, java.lang.Boolean](ScalaBundle.message("column.enabled")) {
        override def getColumnClass: Class[_] = classOf[Boolean]

        override def isCellEditable(item: Node) = true

        override def valueOf(node: Node): lang.Boolean = node.value.map(Boolean.box).orNull

        override def setValue(node: Node, value: java.lang.Boolean): Unit = {
          val toggle = node.value.forall(!_)
          node.value = Some(toggle)
        }
      }

      val model = new ListTreeTableModel(root, Array(new TreeColumnInfo(ScalaBundle.message("column.transformation")), rightColumn)) {
        override def setValueAt(aValue: Any, node: Any, column: Int): Unit = {
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

  override def value: Option[Boolean] =
    if (nodes.forall(_.value.contains(true))) Some(true)
    else if (nodes.forall(_.value.contains(false))) Some(false)
    else None

  override def value_=(b: Option[Boolean]): Unit = {
    nodes.foreach(_.value = b)
  }

  override def transformers: Seq[Transformer] = nodes.flatMap(_.transformers)
}

// TODO remove the default argument when all transformers are implemented
private case class Entry(name: String, private val transformer: Transformer = null, private val enabled: Boolean = true) extends Node(name) {
  var value: Option[Boolean] = Some(enabled)

  override def transformers: Seq[Transformer] = if (value.contains(true)) Seq(transformer) else Seq.empty
}
