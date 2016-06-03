package scala.meta.semantic

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.{api => p, types => ptype}

import scala.collection.immutable.Seq
import scala.meta._
import scala.meta.trees.TreeConverter
import scala.meta.trees.error._
import scala.{meta => m, Seq => _}

class IDEAContext(project: =>Project) extends TreeConverter with semantic.Context {

  override def getCurrentProject = project

  override def dialect: Dialect = dialects.Scala211

  override def typecheck(tree : Tree): Tree = {
//    def doTypecheck(root: Tree) = {
//      val psi = ScalaPsiElementFactory.createBlockExpressionWithoutBracesFromText(tree.toString(), PsiManager.getInstance(project)).asInstanceOf[ScBlock]
//      ideaToMeta(psi) match {
//        case mem: m.Member => mem
//      }
//      psi.lastExpr match {
//        case Some(expr: ScExpression) => toType(expr.getType())
//        case Some(other) => other ?!
//        case None => psi.lastStatement match {
//          case Some(v: ScValue) => toType(v.getType(TypingContext.empty))
//          case Some(other) => other ?!
//          case None => unreachable
//
//        }
//      }
//    }

//    @tailrec
//    def walkUp(t: Tree): Tree = {
//      t.parent match {
//        case Some(t1) => walkUp(t1)
//        case None => t
//      }
//    }

//    if (tree.isTypechecked) return tree
//    val parent = walkUp(tree)
//    val typechecked = doTypecheck(parent)
//    typechecked.children(parent.children.indexOf(tree))
    tree
  }

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
//      case _: m.Import.Selector => ???
    }
  }

  override def members(tpe: Type): Seq[Member] = {
    tpe match {
      case tp@m.Type.Name(value) => getMembers(tp)
      case tp@m.Type.Apply(tpe, _) => members(tpe)
      case m.Type.Select(_, name) => getMembers(name)
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

  override def lub(tpes: Seq[Type]): Type = ???

  override def glb(tpes: Seq[Type]): Type = ???

  override def widen(tpe: Type): Type = ???

  override def dealias(tpe: Type): Type = ???

  override def isSubtype(tpe1 : Type, tpe2 : Type) : Boolean = ???

  override def supertypes(tpe : Type) : Seq[Type] = {
    val ptp = fromType(tpe.asInstanceOf[m.Type])
    ptp match {
      case ptype.api.designator.ScDesignatorType(elem) =>
        elem match {
          case t: ScTemplateDefinition =>
            t.superTypes.map(toType(_))
          case _ =>
            Seq.empty
        }
      case ptype.api.designator.ScProjectionType(ptype.api.designator.ScThisType(clazz), td: ScTemplateDefinition, superRef) =>
        Seq(td.extendsBlock.superTypes.map(toType(_))    :_*)
      case _ =>
        Seq.empty
    }
  }

  override def supermembers(member : Member) : Seq[Member] = {
    val name = member match {
      case t@m.Defn.Class(_, name, _, _, _) => name
      case t@m.Defn.Object(_, name, _)   => name
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

  override def submembers(member: Member): Seq[Member] = ???

}
