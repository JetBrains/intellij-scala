package org.jetbrains.plugins.scala.meta.trees

import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.{api => p, types => ptype}

import scala.meta.internal.{ast => m, semantic => h}
import scala.{Seq => _}

trait Utils {
  self: TreeConverter =>

  val LOG = Logger.getInstance(this.getClass)
  
  val rootPackageName = m.Term.Name("_root_").withDenot(denot = h.Denotation.Single(h.Prefix.Zero, h.Symbol.RootPackage))
  val rootPackagePrefix = h.Prefix.Type(m.Type.Singleton(rootPackageName))

  class UnmatchedTree(msg: String) extends RuntimeException(msg)

  implicit class RichPatTpeTree(ptpe: m.Type) {
    def patTpe: m.Pat.Type = {
      def loop(ptpe: m.Type): m.Pat.Type = {
        ptpe match {
          case ptpe: m.Type.Name => ptpe
          case ptpe: m.Type.Select => ptpe
          case m.Type.Project(pqual, pname) => m.Pat.Type.Project(loop(pqual), pname)
          case ptpe: m.Type.Singleton => ptpe
          case m.Type.Apply(ptpe, args) => m.Pat.Type.Apply(loop(ptpe), args.map(loop))
          case m.Type.ApplyInfix(plhs, pop, prhs) => m.Pat.Type.ApplyInfix(loop(plhs), pop, loop(prhs))
          case m.Type.Function(pparams, pres) => m.Pat.Type.Function(pparams.map(param => loop(param.asInstanceOf[m.Type])), loop(pres))
          case m.Type.Tuple(pelements) => m.Pat.Type.Tuple(pelements.map(loop))
          case m.Type.Compound(ptpes, prefinement) => m.Pat.Type.Compound(ptpes.map(loop), prefinement)
          case m.Type.Existential(ptpe, pquants) => m.Pat.Type.Existential(loop(ptpe), pquants)
          case m.Type.Annotate(ptpe, pannots) => m.Pat.Type.Annotate(loop(ptpe), pannots)
          case m.Type.Placeholder(_) => m.Pat.Type.Wildcard() // FIXME: wtf? is it supposed to convert this way?
          case ptpe: m.Pat.Type.Placeholder => ptpe
          case ptpe: m.Lit => ptpe
        }
      }
      loop(ptpe)
    }
  }

  implicit class RichPSI(psi: PsiElement) {
    def ?! = throw new ScalaMetaUnexpectedPSI(psi)
  }

  def unreachable = throw new ScalaMetaUnreachableException
  def unreachable(reason: String) = throw new ScalaMetaUnreachableException(reason)

}
