package scala.meta

import org.jetbrains.plugins.scala.lang.macros.expansion.MacroExpandAction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.SyntheticMembersInjector

class MetaAnnotationInjector extends SyntheticMembersInjector {
  override def injectMembers(source: ScTypeDefinition): Seq[String] = {
    import scala.meta._
    val annot = source.annotations.find(_.isMetaAnnotation)
    if (annot.nonEmpty) {
      val result = MacroExpandAction.runMetaAnnotation(annot.get)
      result match {
        case Defn.Object(mods, name, templ) =>
          templ.stats
            .getOrElse(Seq.empty)
            .filter(_.isInstanceOf[Member])
            .map(_.toString())
        case Defn.Class(mods, name, tparams, _, templ) => Seq.empty
        case Defn.Trait(mods, name, tparams, _, templ) => Seq.empty
        case _ => Seq.empty
      }
    }
    else Seq.empty
  }
}
