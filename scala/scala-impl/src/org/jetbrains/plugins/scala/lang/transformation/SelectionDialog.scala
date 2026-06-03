package org.jetbrains.plugins.scala.lang.transformation

import com.intellij.openapi.client.ClientSystemInfo
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.profile.codeInspection.ui.table.ThreeStateCheckBoxRenderer
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.treetable.{ListTreeTableModel, TreeColumnInfo, TreeTable}
import com.intellij.util.ui.ColumnInfo
import org.jdesktop.swingx.treetable.DefaultMutableTreeTableNode
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.BooleanExt
import org.jetbrains.plugins.scala.lang.transformation.annotations._
import org.jetbrains.plugins.scala.lang.transformation.calls._
import org.jetbrains.plugins.scala.lang.transformation.declarations.{ExpandProcedureSyntax, MakeResultExpressionExplicit}
import org.jetbrains.plugins.scala.lang.transformation.functions.{ExpandEtaExpansion, ExpandPlaceholderSyntax, MakeEtaExpansionExplicit}
import org.jetbrains.plugins.scala.lang.transformation.general._
import org.jetbrains.plugins.scala.lang.transformation.implicits._
import org.jetbrains.plugins.scala.lang.transformation.references._
import org.jetbrains.plugins.scala.lang.transformation.types._

import java.awt.Dimension
import java.lang
import javax.swing._
import javax.swing.tree.{DefaultTreeCellRenderer, TreeNode}

class SelectionDialog {
  private val RootGroup = Group("root",
    Group(ScalaBundle.message("desugar.group.method.invocations"),
      Entry(ScalaBundle.message("desugar.expand.apply.call"), new ExpandApplyCall()),
      Entry(ScalaBundle.message("desugar.expand.update.call"), new ExpandUpdateCall()),
      Entry(ScalaBundle.message("desugar.expand.unary.call"), new ExpandUpdateCall()),
      Entry(ScalaBundle.message("desugar.expand.property.setter.call"), new ExpandSetterCall()),
      Entry(ScalaBundle.message("desugar.expand.assignment.call"), new ExpandAssignmentCall()),
      Entry(ScalaBundle.message("desugar.expand.dynamic.call"), new ExpandDynamicCall()),
      Entry(ScalaBundle.message("desugar.canonize.infix.call"), new CanonizeInfixCall()),
      Entry(ScalaBundle.message("desugar.canonize.postfix.call"), new CanonizePostifxCall()),
      Entry(ScalaBundle.message("desugar.canonize.arity.0.call"), new CanonizeZeroArityCall()),
      Entry(ScalaBundle.message("desugar.canonize.block.argument"), new CanonizeBlockArgument()),
      Entry(ScalaBundle.message("desugar.expand.auto.tupling"), new ExpandAutoTupling()),
      Entry(ScalaBundle.message("desugar.expand.vararg.argument"), new ExpandVarargArgument(), enabled = false),
      Entry(ScalaBundle.message("desugar.inscribe.default.arguments"), enabled = false),
      Entry(ScalaBundle.message("desugar.expand.to.equals.call"))
    ),
    Group(ScalaBundle.message("desugar.group.type.annotations"),
      Entry(ScalaBundle.message("desugar.value.definition"), new AddTypeToValueDefinition()),
      Entry(ScalaBundle.message("desugar.variable.definition"), new AddTypeToVariableDefinition()),
      Entry(ScalaBundle.message("desugar.method.definition"), new AddTypeToMethodDefinition()),
      Entry(ScalaBundle.message("desugar.function.parameter"), new AddTypeToFunctionParameter()),
      Entry(ScalaBundle.message("desugar.underscore.parameter"), new AddTypeToUnderscoreParameter()),
      Entry(ScalaBundle.message("desugar.reference.pattern"), new AddTypeToReferencePattern()),
      Entry(ScalaBundle.message("desugar.type.parameters"))
    ),
    Group(ScalaBundle.message("desugar.group.types"),
      Entry(ScalaBundle.message("desugar.expand.function.type"), new ExpandFunctionType()),
      Entry(ScalaBundle.message("desugar.expand.tuple.type"), new ExpandTupleType()),
      Entry(ScalaBundle.message("desugar.expand.type.alias")),
      Entry(ScalaBundle.message("desugar.expand.context.bound")),
      Entry(ScalaBundle.message("desugar.expand.view.bound")),
      Entry(ScalaBundle.message("desugar.substitute.anyref"))
    ),
    Group(ScalaBundle.message("desugar.group.implicits"),
      Entry(ScalaBundle.message("desugar.expand.implicit.conversion"), new ExpandImplicitConversion()),
      Entry(ScalaBundle.message("desugar.inscribe.implicit.parameters"), new InscribeImplicitParameters())
    ),
    Group(ScalaBundle.message("desugar.group.functions"),
      Entry(ScalaBundle.message("desugar.expand.placeholder.syntax"), new ExpandPlaceholderSyntax()),
      Entry(ScalaBundle.message("desugar.expand.eta.expansion"), new ExpandEtaExpansion()),
      Entry(ScalaBundle.message("desugar.make.eta.expansion.explicit"), new MakeEtaExpansionExplicit()),
      Entry(ScalaBundle.message("desugar.expand.single.abstract.methods"), enabled = false),
      Entry(ScalaBundle.message("desugar.expand.function.instantiation"), enabled = false)
    ),
    Group(ScalaBundle.message("desugar.group.expressions"),
      Entry(ScalaBundle.message("desugar.expand.for.comprehensions"), new ExpandForComprehension()),
      Entry(ScalaBundle.message("desugar.expand.string.interpolation"), new ExpandStringInterpolation()),
      Entry(ScalaBundle.message("desugar.expand.tuple.instantiation"), new ExpandTupleInstantiation())
    ),
    Group(ScalaBundle.message("desugar.group.declarations"),
      Entry(ScalaBundle.message("desugar.expand.procedure.syntax"), new ExpandProcedureSyntax()),
      Entry(ScalaBundle.message("desugar.make.method.return.expressions.explicit"), new MakeResultExpressionExplicit()),
      Entry(ScalaBundle.message("desugar.add.explicit.override.modifier")),
      Entry(ScalaBundle.message("desugar.replace.underscore.section.with.default.value")),
      Entry(ScalaBundle.message("desugar.expand.property.declaration"), enabled = false),
      Entry(ScalaBundle.message("desugar.expand.property.definition"), enabled = false),
      Entry(ScalaBundle.message("desugar.convert.implicit.class.to.class.and.function"))
    ),
    Group(ScalaBundle.message("desugar.group.references"),
      Entry(ScalaBundle.message("desugar.expand.wildcard.import")),
      Entry(ScalaBundle.message("desugar.fully.qualify.import.expression")),
      Entry(ScalaBundle.message("desugar.partially.qualify.simple.reference"), new PartiallyQualifySimpleReference()),
      Entry(ScalaBundle.message("desugar.fully.qualify.reference"), enabled = false)
    ),
    Group(ScalaBundle.message("desugar.group.general"),
      Entry(ScalaBundle.message("desugar.append.semicolon"), new AppendSemicolon(), enabled = false),
      Entry(ScalaBundle.message("desugar.inscribe.explicit.braces"), enabled = false),
      Entry(ScalaBundle.message("desugar.enforce.parentheses.in.constructor.invocation")),
      Entry(ScalaBundle.message("desugar.convert.parentheses.to.braces.in.for.comprehensions")),
      Entry(ScalaBundle.message("desugar.expand.macro"), enabled = false)
    )
  )

  def show(@Nls title: String): Option[Set[Transformer]] = {
    val dialog = new MyDialog(RootGroup)
    dialog.setTitle(title)

    dialog.showAndGet().option(RootGroup.transformers.filterNot(_ == null).toSet)
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

      val result = new JBScrollPane(table)
      result.setPreferredSize(new Dimension(350, 450))

      result
    }
  }

  private def padding = if (ClientSystemInfo.isMac) 10 else 0
}

private abstract class Node(@Nls name: String) extends DefaultMutableTreeTableNode(name) {
  var value: Option[Boolean]

  def transformers: Seq[Transformer]
}

private case class Group(@Nls name: String, nodes: Node*) extends Node(name) {
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
private case class Entry(@Nls name: String, private val transformer: Transformer = null, private val enabled: Boolean = true) extends Node(name) {
  var value: Option[Boolean] = Some(enabled)

  override def transformers: Seq[Transformer] = if (value.contains(true)) Seq(transformer) else Seq.empty
}
