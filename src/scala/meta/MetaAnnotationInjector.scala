package scala.meta

import java.io.IOException

import org.jetbrains.plugins.scala.lang.macros.expansion.MacroExpandAction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.SyntheticMembersInjector

/**
  * @deprecated meta engine switched to processDeclarations instead of injecting members
  */
class MetaAnnotationInjector extends SyntheticMembersInjector {

  var lastResult: Tree = _

  override def injectMembers(source: ScTypeDefinition): Seq[String] = {
    val annot = source.annotations.find(_.isMetaAnnotation)
    if (annot.nonEmpty) {
      val result = try {
        MacroExpandAction.runMetaAnnotation(annot.get)
      } catch {
        case _: IOException | _: ClassNotFoundException => lastResult
      }
      lastResult = result
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
