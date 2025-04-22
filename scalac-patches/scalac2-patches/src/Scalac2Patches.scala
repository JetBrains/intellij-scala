import scala.tools.nsc.plugins.{Plugin, PluginComponent}
import scala.tools.nsc.{Global, Phase}

class Scalac2Patches(val global: Global) extends Plugin {
  import global._

  override val name = "scalac2-patches"

  override val description = "scalac2 patches"

  override val components = List(new MyComponent(): PluginComponent)

  private class MyComponent extends PluginComponent {
    override val global: Scalac2Patches.this.global.type = Scalac2Patches.this.global

    override val runsAfter = List[String]("pickler")

    override val runsBefore = List[String]("refchecks")

    override val phaseName = Scalac2Patches.this.name

    override def newPhase(prev: Phase): Phase = new MyPhase(prev)

    private class MyPhase(prev: Phase) extends StdPhase(prev) {
      private final val Suffix = "_FORWARDER"

      override def name = Scalac2Patches.this.name

      private val transformer = new Transformer() {
        override def transformTemplate(tree: global.Template): global.Template = {
          val body = tree.body.map {
            case method: DefDef if method.name.endsWith(Suffix) =>
              method.symbol.name = method.name.dropRight(Suffix.length)
              method
            case tree => tree
          }
          super.transformTemplate(treeCopy.Template(tree, tree.parents, tree.self, body))
        }
      }

      override def apply(unit: CompilationUnit): Unit = transformer.transformUnit(unit)
    }
  }
}
