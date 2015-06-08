package org.jetbrains.plugins.scala.meta.trees

import com.intellij.psi.PsiElement

import scala.meta.internal.ast.Term.Param
import scala.{Seq => _}
import scala.collection.immutable.Seq
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.{api => p}
import org.jetbrains.plugins.scala.lang.psi.{types => ptype}
import scala.meta.internal.{ast=>m}
import scala.meta.internal.{semantic => h}

trait Utils {
  self: Converter =>

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
          case ptpe: m.Type.Placeholder => ptpe
          case ptpe: m.Lit => ptpe
        }
      }
      loop(ptpe)
    }
  }

  implicit class RichPSI(psi: PsiElement) {
    def ?! = {println(msg); throw new UnmatchedTree(msg)}

    def msg: String = {
      s"${psi.getClass} @\n${psi.getText}"
    }
  }


}
