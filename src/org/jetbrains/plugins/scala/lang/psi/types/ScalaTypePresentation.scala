package org.jetbrains.plugins.scala
package lang
package psi
package types

import com.intellij.psi._
import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocumentationProvider
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScReferencePattern}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScTypeUtil

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer

trait ScalaTypePresentation extends api.TypePresentation {
  typeSystem: api.TypeSystem =>

  protected override def typeText(t: ScType, nameFun: PsiNamedElement => String, nameWithPointFun: PsiNamedElement => String): String = {
    def typeSeqText(ts: Seq[ScType], start: String, sep: String, end: String, checkWildcard: Boolean = false): String = {
      ts.map(innerTypeText(_, checkWildcard = checkWildcard)).mkString(start, sep, end)
    }

    def typeTail(need: Boolean) = if (need) ".type" else ""

    def existentialArgWithBounds(wildcard: ScExistentialArgument, argText: String): String = {
      val lowerBoundText = if (wildcard.lower != Nothing) " >: " + innerTypeText(wildcard.lower) else ""
      val upperBoundText = if (wildcard.upper != Any) " <: " + innerTypeText(wildcard.upper) else ""
      s"$argText$lowerBoundText$upperBoundText"
    }

    def typeParamText(param: ScTypeParam, subst: ScSubstitutor): String = {
      def typeText0(tp: ScType) = typeText(subst.subst(tp), nameFun, nameWithPointFun)
      val buffer = new StringBuilder
      if (param.isContravariant) buffer ++= "-"
      else if (param.isCovariant) buffer ++= "+"
      buffer ++= param.name
      param.lowerBound foreach {
        case tp if tp.isNothing =>
        case tp: ScType => buffer ++= s" >: ${typeText0(tp)}"
      }
      param.upperBound foreach {
        case tp if tp.isAny =>
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
      def checkIfStable(elem: PsiElement): Boolean = {
        elem match {
          case _: ScObject | _: ScBindingPattern | _: ScParameter | _: ScFieldId => true
          case _ => false
        }
      }
      val typeTailForProjection = typeTail(checkIfStable(e) && needDotType)

      object StaticJavaClassHolder {
        def unapply(t: ScType): Option[PsiClass] = t match {
          case ScDesignatorType(clazz: PsiClass) if isStaticJavaClass(e) => Some(clazz)
          case ParameterizedType(ScDesignatorType(clazz: PsiClass), _) if isStaticJavaClass(e) => Some(clazz)
          case ScProjectionType(_, clazz: PsiClass, _) if isStaticJavaClass(e) => Some(clazz)
          case _ => None
        }

        private def isStaticJavaClass(elem: PsiElement): Boolean = elem match {
          case c: PsiClass => ScalaPsiUtil.isStaticJava(c)
          case _ => false
        }
      }

      p match {
        case ScDesignatorType(pack: PsiPackage) =>
          nameWithPointFun(pack) + refName
        case ScDesignatorType(named) if checkIfStable(named) =>
          nameWithPointFun(named) + refName + typeTailForProjection
        case ScThisType(obj: ScObject) =>
          nameWithPointFun(obj) + refName + typeTailForProjection
        case ScThisType(_: ScTypeDefinition) if checkIfStable(e) =>
          s"${innerTypeText(p, needDotType = false)}.$refName$typeTailForProjection"
        case p: ScProjectionType if checkIfStable(p.actualElement) =>
          s"${projectionTypeText(p, needDotType = false)}.$refName$typeTailForProjection"
        case StaticJavaClassHolder(clazz) =>
          nameWithPointFun(clazz) + refName
        case _: ScCompoundType | _: ScExistentialType =>
          s"(${innerTypeText(p)})#$refName"
        case _ =>
          val innerText = innerTypeText(p)
          if (innerText.endsWith(".type")) innerText.stripSuffix("type") + refName
          else s"$innerText#$refName"
      }
    }

    def compoundTypeText(compType: ScCompoundType): String = {
      val ScCompoundType(comps, signatureMap, typeMap) = compType
      def typeText0(tp: ScType) = innerTypeText(tp)

      val componentsText = if (comps.isEmpty) Nil else Seq(comps.map {
        case tp@FunctionType(_, _) => "(" + innerTypeText(tp) + ")"
        case tp => innerTypeText(tp)
      }.mkString(" with "))

      val declsTexts = (signatureMap ++ typeMap).flatMap {
        case (s: Signature, rt: ScType) if s.namedElement.isInstanceOf[ScFunction] =>
          val fun = s.namedElement.asInstanceOf[ScFunction]
          val funCopy =
            ScFunction.getCompoundCopy(s.substitutedTypes.map(_.map(_()).toList), s.typeParams.toList, rt, fun)
          val paramClauses = ScalaDocumentationProvider.parseParameters(funCopy, -1)(typeText0)
          val retType = if (!compType.equiv(rt)) typeText0(rt) else "this.type"
          val typeParams = if (funCopy.typeParameters.nonEmpty)
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
        case (_: String, sign: TypeAliasSignature) =>
          val ta = ScTypeAlias.getCompoundCopy(sign, sign.ta)
          val paramsText = if (ta.typeParameters.nonEmpty)
            ta.typeParameters.map(typeParamText(_, ScSubstitutor.empty)).mkString("[", ", ", "]")
          else ""
          val decl = s"type ${ta.name}$paramsText"
          val defnText = ta match {
            case tad: ScTypeAliasDefinition =>
              tad.aliasedType.map {
                case tpe if tpe.isNothing => ""
                case tpe => s" = ${typeText0(tpe)}"
              }.getOrElse("")
            case _ =>
              val (lowerBound, upperBound) = (ta.lowerBound.getOrNothing, ta.upperBound.getOrAny)
              val lowerText = if (lowerBound == Nothing) "" else s" >: ${typeText0(lowerBound)}"
              val upperText = if (upperBound == Any) "" else s" <: ${typeText0(upperBound)}"
              lowerText + upperText
          }
          Seq(decl + defnText)
        case _ => Seq.empty
      }

      val refinementText = if (declsTexts.isEmpty) Nil else Seq(declsTexts.mkString("{\n  ", "\n\n  ", "\n}"))

      (componentsText ++ refinementText).mkString(" ")
    }

    @tailrec
    def existentialTypeText(existType: ScExistentialType, checkWildcard: Boolean, stable: Boolean): String = {
      existType match {
        case ScExistentialType(q, wilds) if checkWildcard && wilds.length == 1 =>
          q match {
            case ScExistentialArgument(name, _, _, _) if name == wilds.head.name =>
              existentialArgWithBounds(wilds.head, "_")
            case _ =>
              existentialTypeText(existType, checkWildcard = false, stable)
          }
        case ex@ScExistentialType(ParameterizedType(des, typeArgs), wilds) =>
          val wildcardsMap = ex.wildcardsMap()
          val replacingArgs = new ArrayBuffer[(ScType, ScExistentialArgument)]()
          val left = wilds.filter {
            case arg: ScExistentialArgument =>
              val seq = wildcardsMap.getOrElse(arg, Seq.empty)
              if (seq.length == 1 && typeArgs.exists(_ eq seq.head)) {
                replacingArgs += ((seq.head, arg))
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
        case namedType: NamedType => namedType.name
        case ScAbstractType(tpt, _, _) => tpt.name.capitalize + api.ScTypePresentation.ABSTRACT_TYPE_POSTFIX
        case f@FunctionType(ret, params) if t.isAliasType.isEmpty =>
          val arrow = ScalaPsiUtil.functionArrow
          typeSeqText(params, "(", ", ", s") $arrow ") + innerTypeText(ret)
        case ScThisType(clazz: ScTypeDefinition) =>
          clazz.name + ".this" + typeTail(needDotType)
        case ScThisType(_) =>
          "this" + typeTail(needDotType)
        case TupleType(Seq(tpe)) =>
          s"Tuple1[${innerTypeText(tpe)}]"
        case TupleType(comps) =>
          typeSeqText(comps, "(",", ",")")
        case ScDesignatorType(e@(_: ScObject | _: ScReferencePattern | _: ScParameter)) =>
          nameFun(e) + typeTail(needDotType)
        case ScDesignatorType(e) =>
          nameFun(e)
        case proj: ScProjectionType if proj != null =>
          projectionTypeText(proj, needDotType)
        case ParameterizedType(des, typeArgs) =>
          innerTypeText(des) + typeSeqText(typeArgs, "[", ", ", "]", checkWildcard = true)
        case JavaArrayType(argument) => s"Array[${innerTypeText(argument)}]"
        case UndefinedType(tpt, _) => "NotInfered" + tpt.name
        case c: ScCompoundType if c != null =>
          compoundTypeText(c)
        case ex: ScExistentialType if ex != null =>
          existentialTypeText(ex, checkWildcard, needDotType)
        case ScTypePolymorphicType(internalType, typeParameters) =>
          typeParameters.map(tp => {
            val lowerBound = if (tp.lowerType.v.equiv(Nothing)) "" else " >: " + tp.lowerType.v.toString
            val upperBound = if (tp.upperType.v.equiv(Any)) "" else " <: " + tp.upperType.v.toString
            tp.name + lowerBound + upperBound
          }).mkString("[", ", ", "] ") + internalType.toString
        case mt@ScMethodType(retType, params, _) =>
          implicit val elementScope = mt.elementScope
          innerTypeText(FunctionType(retType, params.map(_.paramType)), needDotType)
        case _ => ""//todo
      }
    }

    innerTypeText(t)
  }
}
