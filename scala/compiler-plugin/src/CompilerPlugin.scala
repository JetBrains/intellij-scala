import CompilerPlugin.*
import dotty.tools.dotc.ast.tpd
import dotty.tools.dotc.core.Contexts.{Context, ctx}
import dotty.tools.dotc.core.Types.{SingletonType, Type, TypeRef}
import dotty.tools.dotc.plugins.{PluginPhase, StandardPlugin}
import dotty.tools.dotc.printing.PlainPrinter
import dotty.tools.dotc.printing.Texts.Text
import dotty.tools.dotc.report
import dotty.tools.dotc.typer.TyperPhase
import dotty.tools.dotc.util.Property

import scala.annotation.nowarn
import scala.language.implicitConversions

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

    // Only for "transparent inline" after the "typer" phase (but for any "inline" after the "inlining" phase)
    override def transformInlined(tree: tpd.Inlined)(using Context): tpd.Tree =
      val printer = new TypePrinter(ctx.fresh.setSetting(ctx.settings.YtestPickler, true))
      val s = "Type: " + printer.toText(tree.tpe).mkString(9000, false).replace("<root>.this.", "_root_.")
      report.echo(s, tree.srcPos)(using ctx.fresh.setSetting(ctx.settings.YshowSuppressedErrors, true))
      super.transformInlined(tree)

    class TypePrinter(ctx: Context) extends PlainPrinter(ctx):
      override def toText(tp: Type): Text =
        homogenize(tp) match
          case tp: TypeRef =>
            toTextPrefixOf(tp) ~ selectionString(tp)
          case tp => super.toText(tp)

      override def toTextSingleton(tp: SingletonType): Text =
        toTextRef(tp)
