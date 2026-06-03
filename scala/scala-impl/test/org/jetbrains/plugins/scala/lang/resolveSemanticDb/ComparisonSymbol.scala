package org.jetbrains.plugins.scala.lang.resolveSemanticDb

import com.intellij.psi._
import com.intellij.psi.impl.source.PsiAnnotationMethodImpl
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScBlockStatement}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScExtension, ScFunction, ScTypeAlias, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScConstructorOwner, ScGivenDefinition, ScMember, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.{ScPackageLike, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.{ScSyntheticClass, ScSyntheticFunction, SyntheticClasses}

import scala.jdk.CollectionConverters.CollectionHasAsScala

object ComparisonSymbol {
  // sometimes we resolve to AnyRef instead of Object and the other way around... don't bother with these mistakes
  private def stripBases(s: String): String =
    s.stripPrefix("scala/AnyRef#")
      .stripPrefix("scala/Any#")
      .stripPrefix("java/lang/Object#")
      .stripPrefix("java/lang/CharSequence#")

  def fromSemanticDb(s: String): String =
    stripBases(
      s.replaceAll(raw"[^#./()]+\$$package.", "") // ignore package object path part
    )

  def escapedName(s: String): String = {
    def isStart(c: Char): Boolean = c.isUnicodeIdentifierStart || c == '_' || c == '$'
    def isPart(c: Char): Boolean = c.isUnicodeIdentifierPart || c == '$'
    if (s.headOption.contains('`')) s
    else if (s.headOption.forall(isStart) && s.forall(isPart)) s
    else s"`$s`"
  }

  def fromPsi(e: PsiNamedElement): String = {
    val buffer = new StringBuilder()

    def add(s: String): Unit = buffer ++= s

    def addName(name: String): Unit = {
      assert(name != null)
      add(escapedName(name))
    }

    def addFqn(fqn: String): Unit = {
      val parts = fqn.split('.').map(escapedName)
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

          val clazz = synthetics.allClasses().collectFirst {
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
          add("`<init>`(+1).")
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
        case e @ (_: PsiField | _ : ScSyntheticFunction | _: PsiMethod | _: ScValueOrVariable) =>
          var index = indexOf(e)
          assert(index != -1, e)
          // Adjust for different library versions, see SCL-25488
          val i = buffer.toString match {
            case "java/lang/Integer#toString" | "java/util/Vector#add" => 0 // As in JDK 8
            case _ => index
          }
          add(s"(${if (i == 0) "" else s"+$i"}).")
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

  private def indexOf(e: PsiNamedElement): Int = e match {
    case _: ScPrimaryConstructor => 0

    case f: ScFunction if f.isConstructor => f.containingClass match {
      case co: ScConstructorOwner =>
        val overloads = co.secondaryConstructors
        1 + overloads.indexOf(f)
      case _ =>
        throw new java.lang.AssertionError(e)
    }

    case f: ScSyntheticFunction if f.isStringPlusMethod => 0

    case f: ScSyntheticFunction => f.getContainingSyntheticClass match {
      case sc: ScSyntheticClass =>
        val offset = if (sc.qualifiedName == "scala.Int" && f.name == "+") 1 else 0 // No def +(x: String): String
        val overloads = sc.syntheticMethods.get(f.name).asScala.toSeq
        offset + overloads.indexOf(f)
      case _ =>
        throw new java.lang.AssertionError(e)
    }

    case f: ScFunction => f.containingClass match {
      case td: ScTypeDefinition =>
        val parameters = td match {
          case co: ScConstructorOwner => co.parameters.filter(p => p.isValEffectively || p.isVar)
          case _ => Seq.empty
        }
        val overloads = (parameters ++ functionsIn(td.members) ++ td.syntheticMethods).filter(_.name == f.name)
        overloads.indexOf(f)
      case _ =>
        val functions = f.extensionMethodOwner.getOrElse(f).getContext match {
          case f: ScalaFile => functionsIn(f.children.filterByType[ScMember].toSeq)
          case p: ScPackaging => functionsIn(p.immediateMembers)
          case t: ScTemplateBody => functionsIn(t.members)
          case b: ScBlock => functionsIn(b.children.filterByType[ScMember].toSeq)
          case _ => throw new java.lang.AssertionError(e)
        }
        val overloads = functions.filter(_.name == f.name)
        overloads.indexOf(f)
    }

    case m: PsiMethod => m.containingClass match {
      case cls: PsiClass =>
        val overloads = cls.findMethodsByName(m.getName, false)
        overloads.indexOf(m)
      case _ =>
        throw new java.lang.AssertionError(e)
    }

    case _: ScValueOrVariable | _: PsiField => 0

    case _ => throw new java.lang.AssertionError(e)
  }

  private def functionsIn(members: Seq[ScMember]): Seq[ScFunction] = members.flatMap {
    case f: ScFunction => Seq(f)
    case e: ScExtension => e.extensionMethods
    case g: ScGivenDefinition => g.desugaredDefinitions.filterByType[ScFunction]
    case c: ScClass if c.hasModifierProperty("implicit") => c.getSyntheticImplicitMethod.toSeq // Why not in td.syntheticMethods?
    case _ => Seq.empty
  }
}
