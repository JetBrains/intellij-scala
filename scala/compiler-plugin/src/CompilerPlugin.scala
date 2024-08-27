import CompilerPlugin.*
import dotty.tools.dotc.ast.tpd
import dotty.tools.dotc.core.Contexts.{Context, ctx}
import dotty.tools.dotc.plugins.{PluginPhase, StandardPlugin}
import dotty.tools.dotc.report
import dotty.tools.dotc.typer.TyperPhase
import dotty.tools.dotc.util.Property

import scala.annotation.nowarn

class CompilerPlugin extends StandardPlugin:
  override val name = "compiler-plugin"

  override val description = "Compiler plugin"

  @nowarn
  override def init(options: List[String]): List[PluginPhase] = List(new Patches1())

private object CompilerPlugin:
  private val MultilinePart = Property.Key[Unit]

  private class Patches1 extends PluginPhase:
    override def phaseName: String = "compiler-plugin"

    override def runsAfter = Set(TyperPhase.name)

    override def isRunnable(using Context): Boolean = true

    override def transformInlined(tree: tpd.Inlined)(using Context): tpd.Tree =
      report.inform("Type: " + ctx.printer.toText(tree.tpe).mkString(9000, false), tree.srcPos)
      super.transformInlined(tree)
