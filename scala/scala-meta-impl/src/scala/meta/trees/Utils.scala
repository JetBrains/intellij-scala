package scala.meta.trees

import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.{PsiElement, PsiPackage}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScSugarCallExpr, ScTypedExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createTypeFromText
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.{types => ptype}

import scala.collection.immutable.LongMap
import scala.meta.trees.error._
import scala.meta.{ScalaMetaBundle, Term, Type}
import scala.{meta => m, Seq => _}

trait Utils {
  self: TreeConverter =>

  val LOG: Logger = Logger.getInstance(this.getClass)

  def ??? = throw new UnimplementedException("???")

  object std {

    def scalaTypeName(name: String): Type.Name = {
      m.Type.Name(name) //.withAttrs(h.Denotation.Single(std.scalaPackagePrefix, h.Symbol.Global(std.scalaPackageSymbol, h.ScalaSig.Term(name), h.BinarySig.None))).setTypechecked
    }

    val rootPackageName: Term.Name = m.Term.Name("_root_") //.withAttrs(denot = h.Denotation.Single(h.Prefix.None, h.Symbol.RootPackage), typingLike = h.Typing.Recursive)
//    val rootPackagePrefix = h.Prefix.Type(m.Type.Singleton(rootPackageName))//.setTypechecked)

//    val scalaPackageSymbol = h.Symbol.Global(h.Symbol.RootPackage, h.ScalaSig.Term("scala"), h.BinarySig.None)
//    val scalaPackageName = m.Term.Name("scala") //.withAttrs(denot = h.Denotation.Single(rootPackagePrefix, scalaPackageSymbol), h.Typing.Recursive)
//    val scalaPackagePrefix = h.Prefix.Type(m.Type.Singleton(scalaPackageName))//.setTypechecked)

    lazy val anyTypeName: Type.Name = scalaTypeName("Any")
    lazy val anyRefTypeName: Type.Name = scalaTypeName("AnyRef")
    lazy val anyValTypeName: Type.Name = scalaTypeName("AnyVal")
    lazy val nothingTypeName: Type.Name = scalaTypeName("Nothing")
    lazy val nullTypeName: Type.Name = scalaTypeName("Null")
    lazy val singletonTypeName: Type.Name = scalaTypeName("Singleton")

    // boxed stuff
    lazy val unit: Type.Name = scalaTypeName("Unit")
    lazy val boolean: Type.Name = scalaTypeName("Boolean")
    lazy val char: Type.Name = scalaTypeName("Char")
    lazy val int: Type.Name = scalaTypeName("Int")
    lazy val float: Type.Name = scalaTypeName("Float")
    lazy val double: Type.Name = scalaTypeName("Double")
    lazy val byte: Type.Name = scalaTypeName("Byte")
    lazy val short: Type.Name = scalaTypeName("Short")

  }

  implicit class RichPatTpeTree(ptpe: m.Type) {
    def stripped: Type = ptpe match {
      case m.Type.Select(_, n:m.Type.Name) => n
      case m.Type.Project(_, n:m.Type.Name) => n
      case other: m.Type => other
      case _ => unreachable
    }
  }

  def mkSyntheticMethodName(owner: m.Type, elem: ScSyntheticFunction, context: ScSugarCallExpr): m.Term.Name = {
    m.Term.Name(elem.name)
//      .withAttrs(
//        denot = h.Denotation.Single(h.Prefix.Type(owner), fqnameToSymbol(owner.toString()+s".${elem.name}")),
//        typingLike = h.Typing.Nonrecursive(toType(context.getTypeWithCachedSubst(TypingContext.empty))))
//      .setTypechecked
  }

  implicit class RichPSI(psi: PsiElement) {
    def ?! = throw new AbortException(psi, ScalaMetaBundle.message("unexpected.psi", psi.getClass, psi.getText))
    def ??? = throw new UnimplementedException(psi)
    def isSingletonType: Boolean = psi match {
      case _: PsiPackage => true
      case _: ScObject   => true
      case _ => false
    }
  }

  implicit class RichScExpression(expr: ScExpression) {
    import expr.projectContext

    def withSubstitutionCaching[T](fun: TypeResult => T): T = {
      if (dumbMode) {
        val maybeType = expr match {
          case ts: ScTypedExpression => createTypeFromText(ts.getText, expr, null)
          case _ => None
        }

        fun(maybeType.asTypeResult)
      } else {
        try {
          ScSubstitutor.cacheSubstitutions = true
          val tp = expr.`type`()
          ScSubstitutor.cacheSubstitutions = false
          fun(tp)
        } finally {
          ScSubstitutor.cacheSubstitutions = false
          ScSubstitutor.cache = LongMap.empty
        }
      }
    }
  }

  implicit class RichScFunctionDefinition(expr: ScFunctionDefinition) {
    import expr.projectContext

    def getTypeWithCachedSubst: ScType = {
      if (dumbMode) {
        expr.definedReturnType.getOrElse(ptype.api.Any)
      } else {
        val substitutor = ScSubstitutor(ScSubstitutor.cache)
        substitutor(expr.`type`().get)
      }
    }
  }

  implicit class RichScTypedDefinition(expr: ScTypedDefinition) {
    import expr.projectContext

    def getTypeWithCachedSubst: TypeResult = {
      if (dumbMode) {
        expr match {
          case ts: ScTypedExpression => createTypeFromText(ts.getText, expr, null).asTypeResult
          case _ => Right(ptype.api.Any)
        }
      } else {
        val substitutor = ScSubstitutor(ScSubstitutor.cache)
        expr.`type`().map(substitutor)
      }
    }
  }

}
