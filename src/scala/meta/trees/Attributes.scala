package scala.meta.trees

import com.intellij.psi.{PsiClass, PsiElement, PsiMethod, PsiPackage}
import org.jetbrains.plugins.scala.lang.psi.api.ScPackage
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.ScPackageImpl
import org.jetbrains.plugins.scala.lang.psi.{api => p, impl, types => ptype}

import scala.language.postfixOps
import scala.meta.internal.{ast => m, semantic => h}

trait Attributes {
  self: TreeConverter =>


  protected implicit class RichAttributesTree[T <: m.Tree](ptree: T) {

    def fqnameToPrefix(fqn: String): h.Prefix = {
      fqn
        .split('.')
        .dropRight(1)
        .foldLeft(std.rootPackagePrefix) {
          (parent, name) => h.Prefix.Type(m.Type.Singleton(
            m.Term.Name(name).withAttrs(denot = h.Denotation.Single(parent, fqnameToSymbol(fqn.substring(0, fqn.indexOf(name) + name.length), toDrop = 0)),
                                        typingLike = h.Typing.Recursive)
            ).setTypechecked
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
            h.Denotation.Single(h.Prefix.Type(m.Type.Singleton(toTermName(cr.qualifier.get)).setTypechecked), toSymbol(cr))
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

    def withTypingFor[P <: PsiElement](elem: Option[P]): h.Typing = {
      elem match {
        case Some(_: PsiPackage) | Some(_: ScPackage) | Some(_: ScObject) => h.Typing.Recursive
        case Some(psi) =>
          val toType1 = toType(psi)  // eagerify type computation beacuse substitution caches are mutable
          h.Typing.Nonrecursive(toType1)
        case None      => h.Typing.Zero
      }
    }

    def withExpansionFor[P <: PsiElement](elem: P): T = {
      val expanded = ptree match {
        case ptree: m.Term.Name => ptree.withExpansion(h.Expansion.Desugaring(toTermName(elem, insertExpansions = false)))
        case _ => unreachable(s"Cannot expand $elem tree")
      }
      expanded.asInstanceOf[T]
    }

    def withAttrsFor[P <: PsiElement](elem: P): T = withAttrsFor(Some(elem))

    def withAttrsFor[P <: PsiElement](elem: Option[P]): T = {
      val denotatedTree = ptree match {
        case ptree: m.Name.Anonymous => ptree.withAttrs(denot(elem))
        case ptree: m.Name.Indeterminate => ptree.withAttrs(denot(elem))
        case ptree: m.Term.Name => ptree.withAttrs(denot = denot(elem), typingLike = withTypingFor(elem)).setTypechecked
        case ptree: m.Type.Name => ptree.withAttrs(denot(elem))
        // TODO: some ctor refs don't have corresponding constructor symbols in Scala (namely, ones for traits)
        // in these cases, our lsym is going to be a symbol of the trait in question
        // we need to account for that in `symbolTable.convert` and create a constructor symbol of our own
        case ptree: m.Ctor.Name => ptree.withAttrs(denot(elem))
        case _ => unreachable(s"Cannot denotate $elem tree")
      }
      denotatedTree.asInstanceOf[T]
    }

  }
}
