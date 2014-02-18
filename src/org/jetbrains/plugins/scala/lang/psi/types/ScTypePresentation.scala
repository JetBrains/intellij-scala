package org.jetbrains.plugins.scala
package lang
package psi
package types

import com.intellij.psi._
import result.TypingContext
import org.apache.commons.lang.StringEscapeUtils
import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocumentationProvider
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTypeDefinition, ScObject}
import api.statements._
import api.base.patterns.{ScReferencePattern, ScBindingPattern}
import extensions.{toPsiNamedElementExt, toPsiClassExt}
import params.{ScParameter, ScTypeParam}
import refactoring.util.{ScalaNamesUtil, ScTypeUtil}
import collection.mutable.ArrayBuffer
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{ScMethodType, ScTypePolymorphicType}
import scala.annotation.tailrec

trait ScTypePresentation {
  def presentableText(t: ScType) = typeText(t, _.name, {
      case obj: ScObject if Set("scala.Predef", "scala").contains(obj.qualifiedName) => ""
      case pack: PsiPackage => ""
      case e => e.name + "."
    }
  )

  def urlText(t: ScType) = {
    def nameFun(e: PsiNamedElement, withPoint: Boolean): String = {
      e match {
        case obj: ScObject if withPoint && obj.qualifiedName == "scala.Predef" => ""
        case e: PsiClass => "<a href=\"psi_element://" + e.qualifiedName + "\"><code>" +
                StringEscapeUtils.escapeHtml(e.name) +
                "</code></a>" + (if (withPoint) "." else "")
        case pack: PsiPackage if withPoint => ""
        case _ => StringEscapeUtils.escapeHtml(e.name) + "."
      }
    }
    typeText(t, nameFun(_, withPoint = false), nameFun(_, withPoint = true))
  }

  def canonicalText(t: ScType) = {
    def removeKeywords(s: String): String = {
      s.split('.').map(s => if (ScalaNamesUtil.isKeyword(s)) "`" + s + "`" else s).mkString(".")
    }
    def nameFun(e: PsiNamedElement, withPoint: Boolean): String = {
      removeKeywords(e match {
        case c: PsiClass =>
          val qname = c.qualifiedName
          if (qname != null && qname != c.name /* exlude default package*/ ) "_root_." + qname else c.name
        case p: PsiPackage => "_root_." + p.getQualifiedName
        case _ =>
          ScalaPsiUtil.nameContext(e) match {
            case m: ScMember =>
              m.containingClass match {
                case o: ScObject => nameFun(o, withPoint = true) + e.name
                case _ => e.name
              }
            case _ => e.name
          }
      }) + (if (withPoint) "." else "")
    }
    typeText(t, nameFun(_, withPoint = false), nameFun(_, withPoint = true))
  }

  private def typeText(t: ScType, nameFun: PsiNamedElement => String, nameWithPointFun: PsiNamedElement => String): String = {

    def typeSeqText(ts: Seq[ScType], start: String, sep: String, end: String, checkWildcard: Boolean = false): String = {
      ts.map(innerTypeText(_, needDotType = true, checkWildcard = checkWildcard)).mkString(start, sep, end)
    }

    def typeTail(need: Boolean) = if (need) ".type" else ""

    def existentialArgWithBounds(wildcard: ScExistentialArgument, argText: String): String = {
      val lowerBoundText = if (wildcard.lowerBound != types.Nothing) " >: " + innerTypeText(wildcard.lowerBound) else ""
      val upperBoundText = if (wildcard.upperBound != types.Any) " <: " + innerTypeText(wildcard.upperBound) else ""
      s"$argText$lowerBoundText$upperBoundText"
    }

    def typeParamText(param: ScTypeParam, subst: ScSubstitutor): String = {
      def typeText0(tp: ScType) = typeText(subst.subst(tp), nameFun, nameWithPointFun)
      val buffer = new StringBuilder
      if (param.isContravariant) buffer ++= "-"
      else if (param.isCovariant) buffer ++= "+"
      buffer ++= param.name
      param.lowerBound foreach {
        case psi.types.Nothing =>
        case tp: ScType => buffer ++= s" >: ${typeText0(tp)}"
      }
      param.upperBound foreach {
        case psi.types.Any =>
        case tp: ScType => buffer ++= s" <: ${typeText0(tp)}"
      }
      param.viewBound foreach {
        (tp: ScType) => buffer ++= s" <% ${typeText0(tp)}"
      }
      param.contextBound foreach {
        (tp: ScType) =>
          buffer ++= s" : ${typeText0(ScTypeUtil.stripTypeArgs(subst.subst(tp)))}"
      }
      buffer.toString()
    }

    def projectionTypeText(projType: ScProjectionType, needDotType: Boolean): String = {
      val ScProjectionType(p, _, _) = projType
      val e = projType.actualElement
      val refName = e.name
      val typeTailForProjection = e match {
        case _: ScObject | _: ScBindingPattern => typeTail(needDotType)
        case _ => ""
      }
      p match {
        case ScDesignatorType(pack: PsiPackage) =>
          nameWithPointFun(pack) + refName
        case ScDesignatorType(obj: ScObject) =>
          nameWithPointFun(obj) + refName + typeTailForProjection
        case ScDesignatorType(v: ScBindingPattern) =>
          nameWithPointFun(v) + refName + typeTailForProjection
        case ScThisType(obj: ScObject) =>
          nameWithPointFun(obj) + refName + typeTailForProjection
        case ScThisType(td: ScTypeDefinition) if e.isInstanceOf[ScObject] || e.isInstanceOf[ScBindingPattern] =>
          s"${innerTypeText(p, needDotType = false)}.$refName$typeTailForProjection"
        case p: ScProjectionType if p.actualElement.isInstanceOf[ScObject] || p.actualElement.isInstanceOf[ScBindingPattern] =>
          s"${projectionTypeText(p, needDotType = false)}.$refName$typeTailForProjection"
        case ScDesignatorType(clazz: PsiClass) if clazz.getLanguage != ScalaFileType.SCALA_LANGUAGE &&
                e.isInstanceOf[PsiModifierListOwner] &&
                e.asInstanceOf[PsiModifierListOwner].getModifierList.hasModifierProperty("static") =>
          nameWithPointFun(clazz) + refName
        case _: ScCompoundType | _: ScExistentialType =>
          s"(${innerTypeText(p)})#$refName"
        case _ =>
          s"${innerTypeText(p)}#$refName"
      }
    }

    def compoundTypeText(compType: ScCompoundType): String = {
      val ScCompoundType(comps, decls, typeDecls, s) = compType
      def typeText0(tp: ScType) = innerTypeText(s.subst(tp))

      val componentsText = if (comps.isEmpty) Nil else Seq(comps.map(innerTypeText(_)).mkString(" with "))

      val declsTexts = (decls ++ typeDecls).flatMap {
        //todo: make it better including substitution
        case fun: ScFunction =>
          val paramClauses = fun.paramClauses.clauses.map(_.parameters.map(param =>
            ScalaDocumentationProvider.parseParameter(param, typeText0)).mkString("(", ", ", ")")).mkString("")
          val retType = fun.returnType.map {
            tp =>
              val scType: ScType = s.subst(tp)
              if (!compType.equiv(scType)) typeText0(tp) else "this.type"
          }.getOrElse("")
          Seq(s"def ${fun.name}$paramClauses$retType")
        case v: ScValue =>
          v.declaredElements.map(td => {
            val scType: ScType = td.getType(TypingContext.empty).getOrAny
            val text = if (!compType.equiv(scType)) typeText0(scType) else "this.type"
            s"val ${td.name}: $text"
          })
        case v: ScVariable =>
          v.declaredElements.map(td => {
            val scType: ScType = td.getType(TypingContext.empty).getOrAny
            val text = if (!compType.equiv(scType)) typeText0(scType) else "this.type"
            s"var ${td.name}: $text"
          })
        case ta: ScTypeAlias =>
          val paramsText = if (ta.typeParameters.length > 0)
            ta.typeParameters.map(typeParamText(_, s)).mkString("[", ", ", "]")
          else ""
          val decl = s"type ${ta.name}$paramsText"
          val defnText = ta match {
            case tad: ScTypeAliasDefinition =>
              compType.types.get(tad.name) match {
                case Some((lower, upper)) =>
                  s" = ${typeText0(s.subst(upper))}"
                case _ =>
                  tad.aliasedType.map {
                    case psi.types.Nothing => ""
                    case tpe => s" = ${typeText0(tpe)}"
                  }.getOrElse("")
              }
            case _ =>
              val (lowerBound, upperBound) = compType.types.get(ta.name) match {
                case Some((lower, upper)) => (s.subst(lower), s.subst(upper))
                case _ => (ta.lowerBound.getOrNothing, ta.upperBound.getOrAny)
              }
              val lowerText = if (lowerBound == psi.types.Nothing) "" else s" >: ${typeText0(lowerBound)}"
              val upperText = if (upperBound == psi.types.Any) "" else s" <: ${typeText0(upperBound)}"
              lowerText + upperText
          }
          Seq(decl + defnText)
        case _ => Seq.empty[String]
      }

      val refinementText = if (declsTexts.isEmpty) Nil else Seq(declsTexts.mkString("{", "; ", "}"))

      (componentsText ++ refinementText).mkString(" ")
    }

    @tailrec
    def existentialTypeText(existType: ScExistentialType, checkWildcard: Boolean, stable: Boolean): String = {
      existType match {
        case ScExistentialType(q, wilds) if checkWildcard && wilds.length == 1 =>
          q match {
            case ScTypeVariable(name) if name == wilds(0).name =>
              existentialArgWithBounds(wilds(0), "_")
            case ScDesignatorType(a: ScTypeAlias) if a.isExistentialTypeAlias && a.name == wilds(0).name =>
              existentialArgWithBounds(wilds(0), "_")
            case _ =>
              existentialTypeText(existType, checkWildcard = false, stable)
          }
        case ex@ScExistentialType(ScParameterizedType(des, typeArgs), wilds) =>
          val wildcardsMap = ex.wildcardsMap()
          val replacingArgs = new ArrayBuffer[(ScType, ScExistentialArgument)]()
          val left = wilds.filter {
            case arg: ScExistentialArgument =>
              val seq = wildcardsMap.getOrElse(arg, Seq.empty)
              if (seq.length == 1 && typeArgs.exists(_ eq seq(0))) {
                replacingArgs += ((seq(0), arg))
                false
              } else true
          }
          val designatorText = innerTypeText(des)
          val typeArgsText = typeArgs.map {t =>
            replacingArgs.find(_._1 eq t) match {
              case Some((_, wildcard)) => existentialArgWithBounds(wildcard, "_")
              case _ => innerTypeText(t, needDotType = true, checkWildcard)
            }
          }.mkString("[", ", ", "]")
          val existentialArgsText = left.map(arg => existentialArgWithBounds(arg, "type " + arg.name)).mkString("{", "; ", "}")

          if (left.isEmpty) s"$designatorText$typeArgsText"
          else s"($designatorText$typeArgsText) forSome $existentialArgsText"
        case ScExistentialType(q, wilds) =>
          val wildsWithBounds = wilds.map(w => existentialArgWithBounds(w, "type " + w.name))
          wildsWithBounds.mkString(s"(${innerTypeText(q)}) forSome {", "; ", "}")
      }
    }

    def innerTypeText(t: ScType, needDotType: Boolean = true, checkWildcard: Boolean = false): String = {
      t match {
        case ScAbstractType(tpt, lower, upper) =>
          ScTypePresentation.ABSTRACT_TYPE_PREFIX + tpt.name.capitalize
        case StdType(name, _) =>
          name
        case ScFunctionType(ret, params) if !t.isAliasType.isDefined =>
          typeSeqText(params, "(", ", ", ") => ") + innerTypeText(ret)
        case ScThisType(clazz: ScTypeDefinition) =>
          clazz.name + ".this" + typeTail(needDotType)
        case ScThisType(clazz) =>
          "this" + typeTail(needDotType)
        case ScTupleType(comps) =>
          typeSeqText(comps, "(",", ",")")
        case ScDesignatorType(e@(_: ScObject | _: ScReferencePattern | _: ScParameter)) =>
          nameFun(e) + typeTail(needDotType)
        case ScDesignatorType(e) =>
          nameFun(e)
        case proj: ScProjectionType if proj != null =>
          projectionTypeText(proj, needDotType)
        case p: ScParameterizedType if p.getTupleType != None => 
          innerTypeText(p.getTupleType.get, needDotType)
        case ScParameterizedType(des, typeArgs) =>
          innerTypeText(des) + typeSeqText(typeArgs, "[", ", ", "]", checkWildcard = true)
        case j@JavaArrayType(arg) => 
          s"Array[${innerTypeText(arg)}]"
        case ScSkolemizedType(name, _, _, _) => name
        case ScTypeParameterType(name, _, _, _, _) => name
        case ScUndefinedType(tpt: ScTypeParameterType) => "NotInfered" + tpt.name
        case ScTypeVariable(name) => name
        case c: ScCompoundType if c != null =>
          compoundTypeText(c)
        case ex: ScExistentialType if ex != null =>
          existentialTypeText(ex, checkWildcard, needDotType)
        case ScTypePolymorphicType(internalType, typeParameters) =>
          typeParameters.map(tp => {
            val lowerBound = if (tp.lowerType.equiv(types.Nothing)) "" else " >: " + tp.lowerType.toString
            val upperBound = if (tp.upperType.equiv(types.Any)) "" else " <: " + tp.upperType.toString
            tp.name + lowerBound + upperBound
          }).mkString("[", ", ", "] ") + internalType.toString
        case mt@ScMethodType(retType, params, isImplicit) =>
          innerTypeText(ScFunctionType(retType, params.map(_.paramType))(mt.project, mt.scope), needDotType)
        case _ => ""//todo
      }
    }

    innerTypeText(t)
  }

}

object ScTypePresentation {
  val ABSTRACT_TYPE_PREFIX = "_"
}