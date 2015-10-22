package org.jetbrains.plugins.scala.meta.trees

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.{api => p, types => ptype}

import scala.meta.internal.{ast => m, semantic => h}

trait Attributes {
  self: Converter =>


  protected implicit class RichAttributesTree[T <: m.Tree](ptree: T) {

    def denot[P <: PsiElement](elem: Option[P]): h.Denotation = {
      if (elem.isEmpty) h.Denotation.Zero
      else
        elem.get match {
            //reference has a prefix
          case cr: p.base.ScStableCodeReferenceElement if cr.qualifier.isDefined =>
            h.Denotation.Single(h.Prefix.Type(m.Type.Singleton(toTermName(cr.qualifier.get))), toSymbol(cr))
          case cr: p.base.ScStableCodeReferenceElement =>
            h.Denotation.Single(h.Prefix.Zero, toSymbol(cr))
          case re: p.base.patterns.ScBindingPattern =>
            h.Denotation.Single(Option(re.containingClass).map(cc => h.Prefix.Type(toType(cc))).getOrElse(h.Prefix.Zero), toSymbol(re))
          case r: org.jetbrains.plugins.scala.lang.psi.impl.ScPackageImpl =>
            h.Denotation.Single(h.Prefix.Type(toType(r.getParentPackage)), toSymbol(r))
          case td: p.toplevel.typedef.ScTypeDefinition if !td.qualifiedName.contains(".") =>
            h.Denotation.Single(h.Prefix.Zero, toSymbol(td))
          case td: p.toplevel.typedef.ScTypeDefinition =>
            h.Denotation.Single(h.Prefix.Type(toType(td.parent.get)), toSymbol(td))
          case ta: p.statements.ScTypeAlias =>
            h.Denotation.Single(Option(ta.containingClass).map(cc => h.Prefix.Type(toType(cc))).getOrElse(h.Prefix.Zero), toSymbol(ta))
          case mm: p.toplevel.typedef.ScMember =>
            h.Denotation.Single(Option(mm.containingClass).map(cc => h.Prefix.Type(toType(cc))).getOrElse(h.Prefix.Zero), toSymbol(mm))
          case pp: p.statements.params.ScParameter =>
            // FIXME: prefix of a parameter?
            h.Denotation.Single(h.Prefix.Zero, toSymbol(pp))
        }
    }

    def withDenot[P <: PsiElement](elem: P): T = withDenot(Some(elem))

    def withDenot[P <: PsiElement](elem: Option[P]): T = {
      val ptree1 = ptree match {
        case ptree: m.Name.Anonymous => ptree.copy(denot = denot(elem))
        case ptree: m.Name.Indeterminate => ptree.copy(denot = denot(elem))
        case ptree: m.Term.Name => ptree.copy(denot = denot(elem))
        case ptree: m.Type.Name => ptree.copy(denot = denot(elem))
        // TODO: some ctor refs don't have corresponding constructor symbols in Scala (namely, ones for traits)
        // in these cases, our lsym is going to be a symbol of the trait in question
        // we need to account for that in `symbolTable.convert` and create a constructor symbol of our own
        case ptree: m.Ctor.Name => ptree.copy(denot = denot(elem))
        case _ => ???
      }
      ptree
    }
  }

}
