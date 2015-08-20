package org.jetbrains.plugins.scala.meta.semantic

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiManager
import org.jetbrains.plugins.scala.meta.trees.TreeConverter

import scala.meta._
import scala.{Seq => _}
import scala.collection.immutable.Seq
import scala.meta.internal.{ast=>m}

class Context(val project: Project) extends semantic.Context {

  val converter = new TreeConverter {
    override def getCurrentProject = project

    override def findFileByPath(path: String) = {
      val virtualFile = VirtualFileManager.getInstance().findFileByUrl(path)
      PsiManager.getInstance(project).findFile(virtualFile)
    }
  }

  override def dialect = scala.meta.dialects.Scala211

  override def desugar(term: Term): Term = term
  override def tpe(term: Term): Type = m.Type.Name("Any")
  override def tpe(param : scala.meta.Term.Param) = ???
  override def defns(ref: Ref): Seq[Member] = {
    ref match {
      case pname: m.Name => converter.defns(pname)
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
  override def members(tpe: Type): Seq[Member] = ???

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
