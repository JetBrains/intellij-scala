package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScAnnotationsHolder, ScFunction, ScValue}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject, ScTypeDefinition}

import scala.meta._

class MetaSupportInjector extends SyntheticMembersInjector {
  /**
    * This method allows to add custom functions to any class, object or trait.
    * This includes synthetic companion object.
    *
    * Context for this method will be class. So inner types and imports of this class
    * will not be available. But you can use anything outside of it.
    *
    * Injected method will not participate in class overriding hierarchy unless this method
    * is marked with override modifier. Use it carefully, only when this behaviour is intended.
    *
    * @param source class to inject functions
    * @return sequence of functions text
    */
  override def injectFunctions(source: ScTypeDefinition): Seq[String] = {
    source match {
      case po: ScObjectImpl if po.isPackageObject && po.qualifiedName == "scala.meta" =>
        Seq("def meta(f: =>scala.meta.Tree): scala.meta.Tree = ???")
      case _ => Seq.empty
    }
  }

  /**
    * Use this method to mark class or trait, that it requires companion object.
    * Note that object as source is not possible.
    *
    * @param source class or trait
    * @return if this source requires companion object
    */
  override def needsCompanionObject(source: ScTypeDefinition): Boolean = {
    ScalaPsiUtil.getMetaCompanionObject(source).isDefined
  }

  override def injectMembers(source: ScTypeDefinition): Seq[String] = {
    injectForCompanionObject(source) ++ injectForThis(source)
  }

  // theese two implementations must produce identical results on equivalent trees of PSI and scala.meta
  private def hash(member: ScMember): Int = {
    def paramHash(p: ScParameter) = p.typeElement.map(_.text.hashCode).getOrElse(0)
    def paramClauseHash(pc: ScParameterClause) = pc.parameters.foldLeft(0)((a,b)=>a+paramHash(b))
    member match {
      case td: ScTypeDefinition => td.getName.hashCode
      case vd: ScValue => vd.declaredNames.foldLeft(0)((a,b) => a+b.hashCode)
      case fu: ScFunction =>
        fu.name.hashCode +
          13*fu.paramClauses.clauses.foldLeft(0)((a,b)=>a+paramClauseHash(b)) +
          31*fu.returnTypeElement.map(_.text.hashCode).getOrElse(0)
      case other => other.getName.hashCode
    }
  }

  // theese two implementations must produce identical results on equivalent trees of PSI and scala.meta
  private def hash(member: Defn): Int = {
    def collectAllNames(tree: Tree): Seq[Name] = tree match {
      case t:Term.Name => Seq(t)
      case t:Type.Name => Seq(t)
      case other => other.children.flatMap(collectAllNames)
    }
    def patHash(pat: Pat) = collectAllNames(pat).foldLeft(0)((a,b)=>a+b.hashCode())
    member match {
      case Defn.Def(_, name, _, paramss, tpe, _) =>
        name.value.hashCode +
        13*paramss.foldLeft(0)((a, b) => a+b.foldLeft(0)((c,d) => c+ d.name.value.hashCode)) +
        31*tpe.map(_.toString().hashCode).getOrElse(0)
      case Defn.Val(_, pats, _, _) => pats.foldLeft(0)((a,b)=>a+patHash(b))
      case Defn.Var(_, pats, _, _) => pats.foldLeft(0)((a,b)=>a+patHash(b))
      case Defn.Class(_, Type.Name(value), _, _, _) => value.hashCode
      case Defn.Trait(_, Type.Name(value), _, _, _) => value.hashCode
      case Defn.Object(_, Term.Name(value), _) => value.hashCode
      case _ => 0
    }
  }

  private def getDiff(a: ScTypeDefinition, b: Template): Seq[Tree] = {
    val aMembers = a.extendsBlock.members.map(hash).toSet
    val bMembers = b.stats.getOrElse(Seq.empty).flatMap {
      case m: Defn => Some(m)
      case _ => None
    }
    bMembers.filter(m => !aMembers.contains(hash(m))).map(trimBodies)
  }


  // this method injects members extracted from the expansion of companion class of current object
  private def injectForCompanionObject(source: ScTypeDefinition): Seq[String] = {
    val companionExpansion: Either[String, Tree] = source match {
      case obj: ScObject => obj.fakeCompanionClassOrCompanionClass match {
        case ah: ScAnnotationsHolder => ah.getMetaExpansion
        case _ => Left("")
      }
      case _ => Left("")
    }
    companionExpansion match {
      case Right(Term.Block(Seq(_, obj: Defn.Object))) => getDiff(source, obj.templ).map(_.toString())
      case _ => Seq.empty
    }
  }

  private def trimBodies(tree: Tree): Tree = tree match {
    case Defn.Val(mods, pats, decltpe, _) => Defn.Val(mods, pats, decltpe, Term.Name("???"))
    case Defn.Var(mods, pats, decltpe, _) => Defn.Var(mods, pats, decltpe, Some(Term.Name("???")))
    case Defn.Def(mods, name, tparams, paramss, tpe, _) => Defn.Def(mods, name, tparams, paramss, tpe, Term.Name("???"))
    case other => other
  }

  private def injectForThis(source: ScTypeDefinition): Seq[String] = {
    val template = source.getMetaExpansion match {
      case Right(Defn.Class(_, _, _, _, templ))                     => Some(templ)
      case Right(Defn.Trait(_, _, _, _, templ))                     => Some(templ)
      case Right(Defn.Object(_, _, templ))                          => Some(templ)
      case Right(Term.Block(Seq(Defn.Class(_, _, _, _, templ), _))) => Some(templ)
      case Right(Term.Block(Seq(Defn.Trait(_, _, _, _, templ), _))) => Some(templ)
      case Right(Term.Block(Seq(Defn.Object(_, _, templ), _)))      => Some(templ)
      case _ => None
    }
    template.map(getDiff(source, _)).map(defns=>defns.map(t=>trimBodies(t).toString())).getOrElse(Seq.empty)
  }
}
