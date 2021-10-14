package org.jetbrains.plugins.scala.lang.resolveSemanticDb

import com.intellij.psi.impl.source.PsiAnnotationMethodImpl
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScPackageLike
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockStatement
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFun, ScTypeAlias, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.{ScSyntheticClass, ScSyntheticFunction, SyntheticClasses}

object ScalaPluginSymbolPrinter {
  def print(e: PsiNamedElement): Option[String] = {
    val buffer = new StringBuilder()

    def add(s: String): Unit = buffer ++= s

    def escaped(s: String): String =
      if (s.headOption.exists(_.isUnicodeIdentifierStart) && s.forall(_.isUnicodeIdentifierPart)) s
      else s"`$s`"

    def addName(name: String): Unit = {
      assert(name != null)
      add(escaped(name))
    }

    def addFqn(fqn: String): Unit = {
      val parts = fqn.split('.').map(escaped)
      add(parts.mkString("/"))
    }

    def addOwner(e: PsiNamedElement): Unit = {
      (e, e.getContext) match {
        case (o: ScObject, _) if o.isPackageObject =>
          return
        case (_: ScSyntheticClass, _) =>
          return
        case (s: ScSyntheticFunction, _) =>
          val synthetics = SyntheticClasses.get(e.getProject)
          val clazz = synthetics.getAll.find(_.syntheticMethods(GlobalSearchScope.allScope(e.getProject)).contains(s))

          clazz match {
            case Some(clazz) =>
              add("scala/")
              add(clazz.className)
              add("#")
              return
            case None if s.isStringPlusMethod =>
              add("java/lang/String#")
              return
            case None =>
              add("<[error]>")
              return
          }
        case (_: ScBlockStatement, ctx: ScPackaging) =>
          // this is for toplevel statements
          addFqn(ctx.fqn)
          add("/package$package.")
          return
        case _ =>
      }
      e.contexts.takeWhile(!_.is[PsiFile]).collectFirst {
        //case `e` => e.parents.collectFirst { case e: ScNamedElement => e }.foreach(addOwner)
        case ctx: PsiNamedElement => ctx
      } match {
        case Some(e) => addSymName(e)
        case None =>
          val hasPackage = e.getContainingFile match {
            case p: PsiClassOwner if p.getPackageName.nonEmpty => true
            case _ => false
          }
          if (!hasPackage) {
            add("_empty_/")
          }
      }
    }

    def addSymName(e: PsiNamedElement): Unit = {
      if (e.name == null)
        return
      if (e.is[PsiFile])
        return
      addOwner(e)

      e match {
        case _: PsiAnnotationMethodImpl =>
          add("`<init>`().")
        case _ =>
      }

      e match {
        case p@(_: PsiParameter | _: PsiAnnotationMethodImpl) =>
          add("(")
          addName(p.name)
          add(")")
          return
        case c: PsiClass if c.containingClass == null =>
          val qualName = c.qualifiedName
          if (qualName == null) addName(c.name)
          else addFqn(qualName)
        case _ => addName(e.name)
      }

      e match {
        case _: ScPackageLike => add("/")
        case o: ScObject if o.isPackageObject => add("/package.")
        case _: ScObject => add(".")
        //case c: PsiClass if c.isInterface && isInImport => add(".")
        case _: PsiClass | _: PsiType | _: ScTypeAlias | _: ScSyntheticClass => add("#")
        case _: PsiField | _ : ScFun | _: PsiMethod | _: ScValueOrVariable => add("().")
        case _ => add(".")
      }

    }

    addSymName(e)

    Some(buffer.result().replace("scala/runtime/stdLibPatches/", "scala/"))
  }
}
