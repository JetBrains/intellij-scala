import scala.tools.nsc.plugins.{Plugin, PluginComponent}
import scala.tools.nsc.{Global, Phase}
import scala.util.matching.Regex

class ScalacPatches(val global: Global) extends Plugin {
  import global._

  override val name = "scalac-patches"

  override val description = "scalac patches"

  override val components = List(new MyComponent(): PluginComponent)

  private class MyComponent extends PluginComponent {
    override val global: ScalacPatches.this.global.type = ScalacPatches.this.global

    override val runsAfter = List[String]("parser")

    override val runsRightAfter = Some("parser")

    override val phaseName = ScalacPatches.this.name

    override def newPhase(prev: Phase): Phase = new MyPhase(prev)

    private class MyPhase(prev: Phase) extends StdPhase(prev) {
      override def name = ScalacPatches.this.name

      private val CrLf = new Regex("\r\n")

      private object MultilinePart

      private val transformer = new Transformer() {
        override def transform(tree: Tree): Tree = tree match {
          case literal @ Literal(Constant(value: String))
            if (isMultiline(literal) || literal.hasAttachment[MultilinePart.type]) && value.contains("\r\n") =>

            treeCopy.Literal(literal, Constant(CrLf.replaceAllIn(value, "\n")))

          case interpolatedLiteral @ q"StringContext(..$args)" if isMultiline(interpolatedLiteral) =>
            args.foreach(_.updateAttachment(MultilinePart))
            super.transform(tree)

          case _ => super.transform(tree)
        }
        
        private def isMultiline(literal: Tree) = {
          val chars = literal.pos.source.content
          val end = literal.pos.end
          literal.pos.end - literal.pos.start > 6 && chars(end - 3) == '\"' && chars(end - 2) == '\"' && chars(end - 1) == '\"'
        }
      }

      override def apply(unit: CompilationUnit): Unit = transformer.transformUnit(unit)
    }
  }
}
