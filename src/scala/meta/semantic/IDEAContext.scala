package scala.meta.semantic

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.{PsiClass, PsiManager}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

import scala.collection.immutable.Seq
import scala.meta._
import scala.meta.internal.semantic.Typing
import scala.meta.internal.{ast => m}
import scala.meta.trees.TreeConverter
import scala.{Seq => _}

class IDEAContext(project: =>Project) extends TreeConverter with semantic.Context {

  override def getCurrentProject = project

  override def dialect = scala.meta.dialects.Scala211

  override def desugar(term: Term): Term = {
    //TODO
    term
  }

  override def tpe(term: Term): Type = {
    def requireTyped(typing: Typing) = typing match {
      case Typing.Zero => throw new SemanticException(s"implementation restriction: internal cache has no type associated with $term")
      case Typing.Specified(tpe) => tpe
    }
    val tpe = term match {
      case t: m.Term => requireTyped(t.typing)
      case t: m.Term.Param => requireTyped(t.typing)
    }
    tpe.asInstanceOf[Type]
  }

  override def tpe(param: scala.meta.Term.Param) = ???

  override def defns(ref: Ref): Seq[Member] = {
    ref match {
      case pname: m.Name => getDefns(pname)
      case m.Term.Select(_, pname) => defns(pname)
      case m.Type.Select(_, pname) => defns(pname)
      case m.Type.Project(_, pname) => defns(pname)
      case m.Type.Singleton(pref) => defns(pref)
      case m.Ctor.Ref.Select(_, pname) => defns(pname)
      case m.Ctor.Ref.Project(_, pname) => defns(pname)
      case m.Ctor.Ref.Function(pname) => defns(pname)
      case _: m.Import.Selector => ???
    }
  }

  override def members(tpe: Type): Seq[Member] = {
    tpe match {
      case tp@m.Type.Name(value) => getMembers(tp)
      case tp:m.Type.Function => ???
        // TODO: what should we even return in case of function?
        // Scala's FunctionN type via Type.Name? Or type.Function? In second case we can't have a bijective map
        // since Type.Function doesn't capture origin's name
//        Seq(
//          m.Defn.Def(Seq.empty, "apply", tp.params)
//        )
      case other => unreachable(s"Can't get members from non-name type: $tpe")
    }
  }

  override def isSubType(tpe1: Type, tpe2: Type): Boolean = ???

  override def lub(tpes: Seq[Type]): Type = ???

  override def glb(tpes: Seq[Type]): Type = ???

  override def parents(tpe: Type): Seq[Type] = ???

  override def widen(tpe: Type): Type = ???

  override def dealias(tpe: Type): Type = ???

  override def parents(member: Member): Seq[Member] = {
    val name = member match {
      case t@m.Defn.Class(_, name, _, _, _) => name
      case t@m.Defn.Object(_, name, _, _)   => name
      case t@m.Defn.Trait(_, name, _, _, _) => name
      case other => unreachable(s"Can't get parents of a non-class tree: $other")
    }
    val psi = name.denot.symbols.map(fromSymbol)
    val parents = psi.map {
      case t: ScTemplateDefinition =>
        t.supers.map(ideaToMeta(_).asInstanceOf[m.Member])
      case t: PsiClass => t.getSupers.map(ideaToMeta(_).asInstanceOf[m.Member]).toSeq
      case other => unreachable(s"Can't get parents of a non-class psi: $other")
    }
    parents.flatten
  }

  override def children(member: Member): Seq[Member] = ???


  override def domain = ???
}
