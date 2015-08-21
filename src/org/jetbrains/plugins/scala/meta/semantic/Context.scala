package org.jetbrains.plugins.scala.meta.semantic

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiManager
import org.jetbrains.plugins.scala.meta.trees.TreeConverter

import scala.collection.immutable.Seq
import scala.meta._
import scala.meta.internal.semantic.Typing
import scala.meta.internal.{ast => m}
import scala.{Seq => _}

class Context(project: =>Project) extends TreeConverter with semantic.Context {

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
      case other => unreachable(s"Can't get members from non-name type: $tpe")
    }
  }

  override def isSubType(tpe1: Type, tpe2: Type): Boolean = ???

  override def lub(tpes: Seq[Type]): Type = ???

  override def glb(tpes: Seq[Type]): Type = ???

  override def parents(tpe: Type): Seq[Type] = ???

  override def widen(tpe: Type): Type = ???

  override def dealias(tpe: Type): Type = ???

  override def parents(member: Member): Seq[Member] = ???

  override def children(member: Member): Seq[Member] = ???


  override def domain = ???
}
