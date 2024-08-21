import Scalac3Patches.*
import dotty.tools.dotc.ast.tpd
import dotty.tools.dotc.core.Constants.Constant
import dotty.tools.dotc.core.Contexts.{Context, ctx}
import dotty.tools.dotc.core.{Constants, Contexts}
import dotty.tools.dotc.plugins.{PluginPhase, StandardPlugin}
import dotty.tools.dotc.typer.TyperPhase
import dotty.tools.dotc.util.Property

import scala.util.matching.Regex

class Scalac3Patches extends StandardPlugin:
  override val name = "scalac3-patches"

  override val description = "scalac3 patches"

  override def init(options: List[String]): List[PluginPhase] = List(new Patches1(), new Patches2())

private object Scalac3Patches:
  private val MultilinePart = Property.Key[Unit]

  private class Patches1 extends PluginPhase:
    import tpd.*

    override def phaseName = "patches1"

    override def runsAfter = Set(TyperPhase.name)

    override def transformApply(apply: tpd.Apply)(using Contexts.Context): tpd.Tree =
      apply match
        case Interpolator(args) if isMultiline(apply) =>
          args.foreach(_.withAttachment(MultilinePart, ()))
        case _ =>
      super.transformApply(apply)

    private object Interpolator:
      def unapply(tree: Tree)(using Context): Option[List[Literal]] = tree match
        case Apply(Select(Apply(StringContextApply(), List(Typed(Literals(strs), _))), _), List(Typed(SeqLiteral(elems, _), _)))
          if elems.length == strs.length - 1 => Some(strs)
        case _ => None

    private object StringContextApply:
      def unapply(tree: Select)(using Context): Boolean =
        (tree.symbol eq ctx.definitions.StringContextModule_apply) && (tree.qualifier.symbol eq ctx.definitions.StringContextModule)

    private object Literals:
      def unapply(tree: SeqLiteral)(using Context): Option[List[Literal]] = tree.elems match
        case literals if literals.forall(_.isInstanceOf[Literal]) => Some(literals.map(_.asInstanceOf[Literal]))
        case _ => None

  end Patches1

  private class Patches2 extends PluginPhase:
    import tpd.*

    private val CrLf = new Regex("\r\n")

    override def phaseName: String = "patches2"

    override def runsAfter: Set[String] = Set("patches1")

    override def transformLiteral(literal: Literal)(using Contexts.Context): Tree =
      def value = literal.const.value.asInstanceOf[String]
      literal.const.tag match
        case Constants.StringTag if (isMultiline(literal) || literal.hasAttachment(MultilinePart)) && value.contains("\r\n") =>
          Literal(new Constant(CrLf.replaceAllIn(value, "\n"), Constants.StringTag))
        case _ =>
          super.transformLiteral(literal)

  end Patches2

  private def isMultiline(literal: tpd.Tree)(using Contexts.Context) =
    val chars = literal.source.content
    val (start, end) =
      val span = literal.srcPos.span
      (span.start, span.end)
    end - start > 6 && chars(end - 3) == '\"' && chars(end - 2) == '\"' && chars(end - 1) == '\"'
