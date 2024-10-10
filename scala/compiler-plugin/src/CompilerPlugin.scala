import CompilerPlugin.*
import dotty.tools.dotc.ast.tpd
import dotty.tools.dotc.core.Contexts.{Context, ctx}
import dotty.tools.dotc.core.Types.{SingletonType, Type, TypeRef}
import dotty.tools.dotc.plugins.{PluginPhase, StandardPlugin}
import dotty.tools.dotc.printing.PlainPrinter
import dotty.tools.dotc.printing.Texts.Text
import dotty.tools.dotc.report
import dotty.tools.dotc.typer.TyperPhase

import scala.annotation.nowarn
import scala.language.implicitConversions

// Compatible with 3.3+ but can in principle be cross-compiled for earlier versions
class CompilerPlugin extends StandardPlugin:
  override val name = "compiler-plugin"

  override val description = "Compiler plugin"

  // ...(using Context) in 3.5, but we need to also be compatible with prior versions
  @nowarn("cat=deprecation")
  override def init(options: List[String]): List[PluginPhase] = List(new Patches1())

private object CompilerPlugin:
  // To identify the type messages (see UpdateCompilerGeneratedStateListener)
  private final val TypePrefix = "<type>"

  // To delimit the subsequent position text (see MessageRendering.messageAndPos)
  private final val TypeSuffix = "</type>"

  private class Patches1 extends PluginPhase:
    override def phaseName: String = "compiler-plugin"

    // Only for "transparent inline" after the "typer" phase (but for any "inline" after the "inlining" phase)
    override def runsAfter = Set(TyperPhase.name)

    // Print types even if there are errors
    override def isRunnable(using Context): Boolean = true

    override def transformInlined(tree: tpd.Inlined)(using Context): tpd.Tree =
      // Skip recursive inlining
      if (!tree.call.isEmpty) {
        // Use -Ytest-pickler to print canonical types (see PlainPrinter.homogenizedView)
        val printer = new TypePrinter(ctx.fresh.setSetting(ctx.settings.YtestPickler, true))
        // Rather than tree.tpe to avoid possible ErrorType("Type mismatch"), e.g. val x: Foo = bar()
        // tpe is binary-incompatible with 3.2, but compiles
        val tpe = tree.expansion.tpe
        val s = printer.toText(tpe).mkString(Int.MaxValue, false)
          .replace("<root>.this.", "_root_.")
          .replace("$.this.", ".")
        // Echo doesn't require the -verbose option
        // Use -Yshow-suppressed-errors to print the type even if there's an error at the same position (see HideNonSensicalMessages)
        report.echo(TypePrefix + s + TypeSuffix, tree.srcPos)(using ctx.fresh.setSetting(ctx.settings.YshowSuppressedErrors, true))
      }
      super.transformInlined(tree)

    // RefinedPrinter is impossible to customize because we cannot override super.super. methods
    class TypePrinter(ctx: Context) extends PlainPrinter(ctx):
      override def toText(tp: Type): Text =
        homogenize(tp) match
          case tp: TypeRef =>
            // Always print prefix (see PlainPrinter.printWithoutPrefix)
            // toTextPrefixOf(tp) is source-incompatible with 3.2, must be toTextPrefix(tp.prefix)
            toTextPrefixOf(tp) ~ selectionString(tp)
          case tp =>
            super.toText(tp)

      override def toTextSingleton(tp: SingletonType): Text =
        // 123 rather than (123: Int)
        toTextRef(tp)
