package org.jetbrains.plugins.scala.meta.trees

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.ScPackageImpl
import org.jetbrains.plugins.scala.lang.psi.{api => p, types => ptype}

import scala.meta.internal.{ast => m, semantic => h}

trait Attributes {
  self: Converter =>


  protected implicit class RichAttributesTree[T <: m.Tree](ptree: T) {

    def denot[P <: PsiElement](elem: Option[P]): h.Denotation = {
//      def prefix(elem: PsiElement) = h.Prefix.
      def mprefix(elem: PsiElement) = h.Prefix.Type(toType(elem))
      if (elem.isEmpty) h.Denotation.Zero
      else
        elem.get match {
            //reference has a prefix
          case cr: ScStableCodeReferenceElement if cr.qualifier.isDefined =>
            h.Denotation.Single(h.Prefix.Type(m.Type.Singleton(toTermName(cr.qualifier.get))), toSymbol(cr))
          case cr: ScStableCodeReferenceElement =>
            h.Denotation.Single(h.Prefix.Zero, toSymbol(cr))
          case td: ScFieldId =>
            val pref = td.nameContext match {
              case vd: ScValueDeclaration    => Option(vd.containingClass).map(cc => mprefix(cc)).getOrElse(h.Prefix.Zero)
              case vd: ScVariableDeclaration => Option(vd.containingClass).map(cc => mprefix(cc)).getOrElse(h.Prefix.Zero)
              case other => other ?!
            }
            h.Denotation.Single(pref, toSymbol(td))
          case re: patterns.ScBindingPattern =>
            h.Denotation.Single(Option(re.containingClass).map(cc => mprefix(cc)).getOrElse(h.Prefix.Zero), toSymbol(re))
          case r: ScPackageImpl =>
            h.Denotation.Single(mprefix(r.getParentPackage), toSymbol(r))
          case td: ScTypeDefinition if !td.qualifiedName.contains(".") =>
            h.Denotation.Single(h.Prefix.Zero, toSymbol(td))
          case td: ScTypeDefinition =>
            h.Denotation.Single(mprefix(td.parent.get), toSymbol(td))
          case ta: ScTypeAlias =>
            h.Denotation.Single(Option(ta.containingClass).map(cc => mprefix(cc)).getOrElse(h.Prefix.Zero), toSymbol(ta))
          case mm: ScMember =>
            h.Denotation.Single(Option(mm.containingClass).map(cc => mprefix(cc)).getOrElse(h.Prefix.Zero), toSymbol(mm))
          case pp: params.ScParameter =>
            // FIXME: prefix of a parameter?
            h.Denotation.Single(h.Prefix.Zero, toSymbol(pp))
        }
    }

    def withDenot[P <: PsiElement](elem: P): T = withDenot(Some(elem))

    def withDenot[P <: PsiElement](elem: Option[P]): T = {
      val denotatedTree = ptree match {
        case ptree: m.Name.Anonymous => ptree.copy(denot = denot(elem))
        case ptree: m.Name.Indeterminate => ptree.copy(denot = denot(elem))
        case ptree: m.Term.Name => ptree.copy(denot = denot(elem), typing = ptree.typing)
        case ptree: m.Type.Name => ptree.copy(denot = denot(elem))
        // TODO: some ctor refs don't have corresponding constructor symbols in Scala (namely, ones for traits)
        // in these cases, our lsym is going to be a symbol of the trait in question
        // we need to account for that in `symbolTable.convert` and create a constructor symbol of our own
        case ptree: m.Ctor.Name => ptree.copy(denot = denot(elem), typing = ptree.typing)
        case _ => ???
      }
      denotatedTree.asInstanceOf[T]
    }
  }

}
