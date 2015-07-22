package org.jetbrains.plugins.scala.meta.trees

import com.intellij.psi.{PsiMethod, PsiClass, PsiElement}
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.ScPackageImpl
import org.jetbrains.plugins.scala.lang.psi.{api => p, impl, types => ptype}

import scala.meta.internal.{ast => m, semantic => h}

trait Attributes {
  self: TreeConverter =>


  protected implicit class RichAttributesTree[T <: m.Tree](ptree: T) {

    def fqnameToPrefix(fqn: String) = {
      fqn
        .split('.')
        .dropRight(1)
        .foldLeft(rootPackagePrefix) {
          (parent, name) => h.Prefix.Type(m.Type.Singleton(
            m.Term.Name(name, denot =
              h.Denotation.Single(parent, fqnameToSymbol(fqn.substring(0, fqn.indexOf(name) + name.length), toDrop = 0)))
          )
        )
      }
    }

    def denot[P <: PsiElement](elem: Option[P]): h.Denotation = {
      def mprefix(elem: PsiElement, fqn: String = "") = Option(elem).map(cc => h.Prefix.Type(toType(cc))).getOrElse(fqnameToPrefix(fqn))
      if (elem.isEmpty) h.Denotation.Zero
      else
        elem.get match {
            //reference has a prefix
          case cr: ScStableCodeReferenceElement if cr.qualifier.isDefined =>
            h.Denotation.Single(h.Prefix.Type(m.Type.Singleton(toTermName(cr.qualifier.get))), toSymbol(cr))
          case cr: ScStableCodeReferenceElement =>
            h.Denotation.Single(h.Prefix.Zero, toSymbol(cr))
          case td: ScFieldId =>
            val pref = td.nameContext match {  // FIXME: what?
              case vd: ScValueDeclaration    => mprefix(vd.containingClass)
              case vd: ScVariableDeclaration => mprefix(vd.containingClass)
              case other => other ?!
            }
            h.Denotation.Single(pref, toSymbol(td))
          case re: patterns.ScBindingPattern =>
            h.Denotation.Single(mprefix(re.containingClass), toSymbol(re))
          case r: ScPackageImpl =>
            h.Denotation.Single(mprefix(r.getParentPackage), toSymbol(r))
          case td: ScTypeDefinition if !td.qualifiedName.contains(".") =>
            h.Denotation.Single(h.Prefix.Zero, toSymbol(td))
          case td: ScTypeDefinition =>
            h.Denotation.Single(mprefix(td.containingClass, td.qualifiedName), toSymbol(td))
          case sc: impl.toplevel.synthetic.ScSyntheticClass =>
            h.Denotation.Single(fqnameToPrefix(sc.getQualifiedName), toSymbol(sc))
          case mm: ScMember =>
            h.Denotation.Single(mprefix(mm.containingClass), toSymbol(mm))
          case tp: ScTypeParam =>
            h.Denotation.Single(h.Prefix.Zero, toSymbol(tp))
          case pp: params.ScParameter =>
            h.Denotation.Single(h.Prefix.Zero, toSymbol(pp)) // FIXME: prefix of a parameter?
          // Java Stuff starts here
          case pc: PsiClass =>
            h.Denotation.Single(mprefix(pc.getContainingClass, pc.getQualifiedName), toSymbol(pc))
          case pm: PsiMethod =>
            h.Denotation.Single(mprefix(pm.getContainingClass), toSymbol(pm))
          case other => other ?!
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
