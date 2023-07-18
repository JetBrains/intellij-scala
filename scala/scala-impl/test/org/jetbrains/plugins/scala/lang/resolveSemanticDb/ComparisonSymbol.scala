package org.jetbrains.plugins.scala.lang.resolveSemanticDb

import com.intellij.psi._
import com.intellij.psi.impl.source.PsiAnnotationMethodImpl
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScPackageLike
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockStatement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFun, ScTypeAlias, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.{ScSyntheticClass, ScSyntheticFunction, SyntheticClasses}

object ComparisonSymbol {
  // sometimes we resolve to AnyRef instead of Object and the other way around... don't bother with these mistakes
  private def stripBases(s: String): String =
    s.stripPrefix("scala/AnyRef#")
      .stripPrefix("scala/Any#")
      .stripPrefix("java/lang/Object#")
      .stripPrefix("java/lang/CharSequence#")

  def fromSemanticDb(s: String): String =
    stripBases(
      s.replaceAll(raw"\(\+\d+\)", "()") // remove overloading index
      .replaceAll(raw"[^#./()]+\$$package.", "") // ignore package object path part
    )

  def fromPsi(e: PsiNamedElement): String = {
    val buffer = new StringBuilder()

    def add(s: String): Unit = buffer ++= s

    def escaped(s: String): String = {
      def isStart(c: Char): Boolean = c.isUnicodeIdentifierStart || c == '_' || c == '$'
      def isPart(c: Char): Boolean = c.isUnicodeIdentifierPart || c == '$'
      if (s.headOption.contains('`')) s
      else if (s.headOption.forall(isStart) && s.forall(isPart)) s
      else s"`$s`"
    }

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

          val clazz = synthetics.all.collectFirst {
            case synth: ScSyntheticClass if synth.syntheticMethods.values().contains(s) => synth
          }

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
              throw new Exception(s"Cannot create comparison symbol for unknown synthetic function $s")
          }
        case (p: ScClassParameter, _) if p.isClassMember =>
          addSymName(p.containingClass)
          return
        case (_: ScBlockStatement | _: ScTypeAlias, ctx: ScPackaging) =>
          // this is for toplevel statements
          addFqn(ctx.fqn)
          add("/")
          return
        case _ =>
      }
      e.contexts.takeWhile(!_.is[PsiFile]).collectFirst {
        //case `e` => e.parents.collectFirst { case e: ScNamedElement => e }.foreach(addOwner)
        case ctx: PsiNamedElement => ctx
      } match {
        case Some(e) => addSymName(e)
        case None =>
          val file = e.getContainingFile

          val hasPackage =
            if (ScalaPsiElementFactory.SyntheticFileKey.isIn(file)) false
            else file match {
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
      addOwner(e)

      e match {
        case _: PsiAnnotationMethodImpl =>
          add("`<init>`().")
        case _ =>
      }

      e match {
        case p: ScClassParameter if p.isClassMember =>
          addName(p.name)
          add(".")
          return
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
        case _: PsiEnumConstant => add(".")
        case f: PsiField if f.hasModifierProperty(PsiModifier.FINAL) => add(".")
        //case c: PsiClass if c.isInterface && isInImport => add(".")
        case _: PsiClass | _: PsiType | _: ScTypeAlias | _: ScSyntheticClass => add("#")
        case _: PsiField | _ : ScFun | _: PsiMethod | _: ScValueOrVariable => add("().")
        case _ => add(".")
      }

    }

    if (isInRefinement(e)) {
      throw new Exception(s"Cannot create comparison symbol in refinement for $e")
    }

    e match {
      case p: ScPackageLike if p.fqn == "" =>
        add("_root_/")
      case _ =>
        addSymName(e)
    }

    stripBases(buffer.result().replace("scala/runtime/stdLibPatches/", "scala/"))
  }
}
