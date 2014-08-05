package org.jetbrains.plugins.scala
package lang
package psi
package types

import com.intellij.psi._
import org.apache.commons.lang.StringEscapeUtils
import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocumentationProvider
import org.jetbrains.plugins.scala.extensions.{toPsiClassExt, toPsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScReferencePattern}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.refactoring.util.{ScTypeUtil, ScalaNamesUtil}

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer

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
      def isInnerStaticJavaClassForParent(clazz: PsiClass): Boolean = {
        clazz.getLanguage != ScalaFileType.SCALA_LANGUAGE &&
          e.isInstanceOf[PsiModifierListOwner] &&
          e.asInstanceOf[PsiModifierListOwner].getModifierList.hasModifierProperty("static")
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
        case ScDesignatorType(clazz: PsiClass) if isInnerStaticJavaClassForParent(clazz) =>
          nameWithPointFun(clazz) + refName
        case ScParameterizedType(ScDesignatorType(clazz: PsiClass), _) if isInnerStaticJavaClassForParent(clazz) =>
          nameWithPointFun(clazz) + refName
        case _: ScCompoundType | _: ScExistentialType =>
          s"(${innerTypeText(p)})#$refName"
        case _ =>
          s"${innerTypeText(p)}#$refName"
      }
    }

    def compoundTypeText(compType: ScCompoundType): String = {
      val ScCompoundType(comps, signatureMap, typeMap) = compType
      def typeText0(tp: ScType) = innerTypeText(tp)

      val componentsText = if (comps.isEmpty) Nil else Seq(comps.map(innerTypeText(_)).mkString(" with "))

      val declsTexts = (signatureMap ++ typeMap).flatMap {
        case (s: Signature, rt: ScType) if s.namedElement.isInstanceOf[ScFunction] =>
          val fun = s.namedElement.asInstanceOf[ScFunction]
          val funCopy = ScFunction.getCompoundCopy(s.substitutedTypes.map(_.toList), s.typeParams.toList, rt, fun)
          val paramClauses = funCopy.paramClauses.clauses.map(_.parameters.map(param =>
            ScalaDocumentationProvider.parseParameter(param, typeText0)).mkString("(", ", ", ")")).mkString("")
          val retType = if (!compType.equiv(rt)) typeText0(rt) else "this.type"
          val typeParams = if (funCopy.typeParameters.length > 0)
            funCopy.typeParameters.map(typeParamText(_, ScSubstitutor.empty)).mkString("[", ", ", "]")
          else ""
          Seq(s"def ${s.name}$typeParams$paramClauses: $retType")
        case (s: Signature, rt: ScType) if s.namedElement.isInstanceOf[ScTypedDefinition] =>
          if (s.paramLength.sum > 0) Seq.empty
          else {
            s.namedElement match {
              case bp: ScBindingPattern =>
                val b = ScBindingPattern.getCompoundCopy(rt, bp)
                Seq((if (b.isVar) "var " else "val ") + b.name + " : " + typeText0(rt))
              case fi: ScFieldId =>
                val f = ScFieldId.getCompoundCopy(rt, fi)
                Seq((if (f.isVar) "var " else "val ") + f.name + " : " + typeText0(rt))
              case _ => Seq.empty
            }
          }
        case (s: String, sign: TypeAliasSignature) =>
          val ta = ScTypeAlias.getCompoundCopy(sign, sign.ta)
          val paramsText = if (ta.typeParameters.length > 0)
            ta.typeParameters.map(typeParamText(_, ScSubstitutor.empty)).mkString("[", ", ", "]")
          else ""
          val decl = s"type ${ta.name}$paramsText"
          val defnText = ta match {
            case tad: ScTypeAliasDefinition =>
              tad.aliasedType.map {
                case psi.types.Nothing => ""
                case tpe => s" = ${typeText0(tpe)}"
              }.getOrElse("")
            case _ =>
              val (lowerBound, upperBound) = (ta.lowerBound.getOrNothing, ta.upperBound.getOrAny)
              val lowerText = if (lowerBound == psi.types.Nothing) "" else s" >: ${typeText0(lowerBound)}"
              val upperText = if (upperBound == psi.types.Any) "" else s" <: ${typeText0(upperBound)}"
              lowerText + upperText
          }
          Seq(decl + defnText)
        case _ => Seq.empty
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
        case f@ScFunctionType(ret, params) if !t.isAliasType.isDefined =>
          val projectOption = ScType.extractClass(f).map(_.getProject)
          val arrow = projectOption.map(ScalaPsiUtil.functionArrow).getOrElse("=>")
          typeSeqText(params, "(", ", ", s") $arrow ") + innerTypeText(ret)
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
            val lowerBound = if (tp.lowerType().equiv(types.Nothing)) "" else " >: " + tp.lowerType().toString
            val upperBound = if (tp.upperType().equiv(types.Any)) "" else " <: " + tp.upperType().toString
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