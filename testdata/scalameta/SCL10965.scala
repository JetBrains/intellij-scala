
import scala.collection.immutable.Seq
import scala.meta._

class repro extends scala.annotation.StaticAnnotation {
  inline def apply(defn: Any): Any = meta {
    defn match {
      case Term.Block(Seq(t @ ClassOrTraitWithOneTypeParameter(mods, name), companion: Defn.Object))
        if FreeMacro.isSealed(mods) =>

        val oldTemplStats = companion.templ.stats.getOrElse(Nil)
        val subTypes = oldTemplStats.collect {
          case t: Defn.Class if FreeMacro.inherits(name)(t) => t
        }

        val newStats =
          FreeMacro.mkTraitF(name, subTypes) +: oldTemplStats

//        println(newStats)

        val newCompanion =
          companion.copy(templ = companion.templ.copy(stats = Some(newStats)))

        val out = Term.Block(Seq(t, newCompanion))
//        println(out)
        out
    }
  }
}

object FreeMacro {

  def isSealed(mods: Seq[Mod]): Boolean = mods.exists(_.syntax == "sealed")

  def inherits(superType: Type.Name)(cls: Defn.Class): Boolean =
    cls.templ.parents.headOption.exists {
      case q"$parent[$out]()" =>
        parent.syntax == superType.syntax
      case x            => false
    }


  def mkTraitF(superName: Type.Name, subTypes: Seq[Defn.Class]): Stat = {
    def decapitalize(string: String): String =
      string.head.toLower + string.tail

    val stats = subTypes.map {
      case q"..$mods class $name[..$tparams](..$fields) extends $f[$tout]()" =>
        q"def ${Term.Name(decapitalize(name.toString))}[..$tparams](..$fields): F[$tout]"
    }

    q"""
        trait ForF[F[_]] {
          ..$stats
        }
        """
  }
}

object ClassOrTraitWithOneTypeParameter {
  def unapply(any: Defn): Option[(Seq[Mod], Type.Name)] = any match {
    case t: Defn.Class if t.tparams.length == 1 => Some((t.mods, t.name))
    case t: Defn.Trait if t.tparams.length == 1 => Some((t.mods, t.name))
    case x             => None
  }
}