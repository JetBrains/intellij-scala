package scala.meta.trees

import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.{api => p, types => ptype}

import scala.meta.internal.{ast => m, semantic => h}
import scala.{Seq => _}

trait Utils {
  self: TreeConverter =>

  val LOG = Logger.getInstance(this.getClass)
  

  object std {

    def scalaTypeName(name: String) = {
      m.Type.Name(name).withAttrs(h.Denotation.Single(std.scalaPackagePrefix, h.Symbol.Global(std.scalaPackageSymbol, name, h.Signature.Type)))
    }

    val rootPackageName = m.Term.Name("_root_").withAttrs(denot = h.Denotation.Single(h.Prefix.Zero, h.Symbol.RootPackage), typingLike = h.Typing.Recursive)
    val rootPackagePrefix = h.Prefix.Type(m.Type.Singleton(rootPackageName).setTypechecked)

    val scalaPackageSymbol = h.Symbol.Global(h.Symbol.RootPackage, "scala", h.Signature.Term)
    val scalaPackageName = m.Term.Name("scala").withAttrs(denot = h.Denotation.Single(rootPackagePrefix, scalaPackageSymbol), h.Typing.Recursive)
    val scalaPackagePrefix = h.Prefix.Type(m.Type.Singleton(scalaPackageName).setTypechecked)

    lazy val anyTypeName       = scalaTypeName("Any")
    lazy val anyRefTypeName    = scalaTypeName("AnyRef")
    lazy val anyValTypeName    = scalaTypeName("AnyVal")
    lazy val nothingTypeName   = scalaTypeName("Nothing")
    lazy val nullTypeName      = scalaTypeName("Null")
    lazy val singletonTypeName = scalaTypeName("Singleton")

    // boxed stuff
//    lazy val intTypeName = scalaTypeName("Int")
//    lazy val stringTypeName = scalaTypeName("String")
    // ...
  }

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
