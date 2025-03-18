import CompilerPlugin._

import scala.reflect.internal.util.RangePosition
import scala.tools.nsc.plugins.{Plugin, PluginComponent}
import scala.tools.nsc.reporters.ForwardingReporter
import scala.tools.nsc.{Global, Phase}
import scala.util.matching.Regex

class CompilerPlugin(val global: Global) extends Plugin {
  private final val TypePrefix = "<type>"

  private final val TypeSuffix = "</type>"

  override val name = "intellij-compiler-plugin"

  override val description = "IntelliJ compiler plugin"

  override val components = List[PluginComponent](new IntelliJTyper(), new IntelliJCleanup())

  // Suppress errors up to and including the typer phase to run the plugin phase
  private var suppressErrors = true

  // Control whether to run the next phase (see Global.compileUnitsInternal)
  global.reporter = new ForwardingReporter(global.reporter) { // This class is available since 2.13.1
    override def hasErrors = !suppressErrors && super.hasErrors
  }

  private class IntelliJTyper extends PluginComponent {
    override val global: CompilerPlugin.this.global.type = CompilerPlugin.this.global

    override val runsAfter = List("typer")

    override val phaseName = "intellij-typer"

    override def newPhase(prev: Phase): Phase = new StdPhase(prev) {
      import global._

      override def name = "intellij-typer"

      private val transformer = new Transformer() {
        override def transform(tree: Tree): Tree = {
          tree.attachments.get[analyzer.MacroExpansionAttachment] match {
            case Some(analyzer.MacroExpansionAttachment(expandee, expanded: Tree)) if expandee.pos.isInstanceOf[RangePosition] && isWhiteboxMacro(global)(expandee.symbol) =>
              // If there's a type mismatch, the type checker replaces the tree type with an ErrorType (see TyperErrorGen.issueError)
              val tpe = if (tree.tpe.isError) typer.typed(expandee).tpe else tree.tpe
              val s = LiteralTypePattern.replaceAllIn(tpe.toString, _.group(1))
              // echo is not binary compatible between 2.13.11 and 2.13.12 (overloading vs default argument)
              reporter.info(expandee.pos, TypePrefix + s + TypeSuffix, force = true)
            case _ =>
          }
          super.transform(tree)
        }
      }

      override def apply(unit: CompilationUnit): Unit = {
        transformer.transformUnit(unit)
        // Reveal errors after the plugin phase to avoid "unexpected type representation reached the compiler backend"
        suppressErrors = false
      }
    }
  }

  private class IntelliJCleanup extends PluginComponent {
    override val global: CompilerPlugin.this.global.type = CompilerPlugin.this.global

    override val runsAfter = List("cleanup")

    override val phaseName = "intellij-cleanup"

    override def newPhase(prev: Phase): Phase = new StdPhase(prev) {
      import global._

      override def name = "intellij-cleanup"

      override def apply(unit: CompilationUnit): Unit = {
        // Reset before the next compilation unit
        suppressErrors = true
      }
    }
  }
}

private object CompilerPlugin {
  val LiteralTypePattern = new Regex("(?:Boolean|Int|Long|Float|Double|Char|String)\\((.+?)\\)")

  def isWhiteboxMacro(global: Global)(symbol: global.Symbol): Boolean = {
    import global._

    // See scala.tools.nsc.typechecker.Macros.MacroImplBinding
    symbol.annotations.exists { annotation =>
      annotation.tpe.toString == "scala.reflect.macros.internal.macroImpl" && {
        val args = annotation.args match {
          case List(Apply(_, args)) => args
          case List(TypeApply(Apply(_, args), _)) => args
          case _ => List.empty
        }
        args.exists {
          case Assign(Literal(Constant("isBlackbox")), Literal(Constant(false))) => true
          case _ => false
        }
      }
    }
  }
}
