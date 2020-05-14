package org.jetbrains.plugins.scala
package lang
package psi
package types

import com.intellij.psi._
import org.jetbrains.plugins.scala.codeInspection.typeLambdaSimplify.KindProjectorSimplifyTypeProjectionInspection
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.InfixExpr
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScReferencePattern}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScTypeBoundsOwner, ScTypeParametersOwner, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.api.TypePresentation.{NameRenderer, PresentationOptions, TextEscaper}
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

import scala.annotation.tailrec

trait ScalaTypePresentation extends api.TypePresentation {
  typeSystem: api.TypeSystem =>

  import ScalaTypePresentation._

  override protected def typeText(
    `type`: ScType,
    nameRenderer: NameRenderer,
    textEscaper: TextEscaper,
    options: PresentationOptions
  )(implicit context: TypePresentationContext): String = {
    val boundsRenderer = new TypeBoundsRenderer(textEscaper)
    import boundsRenderer._

    def typesText(types: Seq[ScType]): String = types
      .map(innerTypeText(_))
      .commaSeparated(model = Model.Parentheses)

    def typeTail(need: Boolean) = if (need) ObjectTypeSuffix else ""

    def typeParametersText(paramsOwner: ScTypeParametersOwner, substitutor: ScSubstitutor): String = paramsOwner.typeParameters match {
      case Seq()  => ""
      case params => params.map(typeParamText(_, substitutor)).commaSeparated(model = Model.SquareBrackets)
    }

    def typeParamText(param: ScTypeParam, substitutor: ScSubstitutor): String = {
      def typeText0(tp: ScType) = typeText(substitutor(tp), nameRenderer, textEscaper, options)

      boundsRenderer.render(
        param.name,
        param.lowerBound.toOption,
        param.upperBound.toOption,
        param.viewBound,
        param.contextBound
      )(typeText0)
    }

    def typeParameterTypeText(typeParameterType: TypeParameterType): String = {
      val (viewTypes, contextTypes) = typeParameterType.typeParameter.psiTypeParameter match {
        case boundsOwner: ScTypeBoundsOwner => (boundsOwner.viewBound, boundsOwner.contextBound)
        case _                              => EmptyTuple
      }
      boundsRenderer.render(
        typeParameterType.name,
        Some(typeParameterType.lowerType),
        Some(typeParameterType.upperType),
        viewTypes,
        contextTypes
      )(innerTypeText(_))
    }

    def projectionTypeText(projType: ScProjectionType, needDotType: Boolean): String = {
      val e = projType.actualElement

      def checkIfStable(element: PsiElement): Boolean = element match {
        case _: ScObject | _: ScBindingPattern | _: ScParameter | _: ScFieldId => true
        case _ => false
      }

      val isStaticJavaClass = e match {
        case c: PsiClass => ScalaPsiUtil.isStaticJava(c)
        case _ => false
      }

      val typeTailForProjection = typeTail(checkIfStable(e) && needDotType)

      object StaticJavaClassHolder {
        def unapply(t: ScType): Option[PsiClass] = t match {
          case ScDesignatorType(clazz: PsiClass)                       => Some(clazz)
          case ParameterizedType(ScDesignatorType(clazz: PsiClass), _) => Some(clazz)
          case ScProjectionType(_, clazz: PsiClass)                    => Some(clazz)
          case _                                                       => None
        }
      }

      val refName = e.name
      val escapedName =
        if (options.renderProjectionTypeName) nameRenderer.renderName(e)
        else nameRenderer.escapeName(refName)

      if (context.nameResolvesTo(refName, e)) escapedName
      else
        projType.projected match {
          case ScDesignatorType(pack: PsiPackage) =>
            nameRenderer.renderNameWithPoint(pack) + escapedName
          case ScDesignatorType(named) if checkIfStable(named) =>
            nameRenderer.renderNameWithPoint(named) + escapedName + typeTailForProjection
          case ScThisType(obj: ScObject) =>
            nameRenderer.renderNameWithPoint(obj) + escapedName + typeTailForProjection
          case p@ScThisType(_: ScTypeDefinition) if checkIfStable(e) =>
            s"${innerTypeText(p, needDotType = false)}.$escapedName$typeTailForProjection"
          case p: ScProjectionType if checkIfStable(p.actualElement) =>
            s"${projectionTypeText(p, needDotType = false)}.$escapedName$typeTailForProjection"
          case StaticJavaClassHolder(clazz) if isStaticJavaClass =>
            nameRenderer.renderNameWithPoint(clazz) + escapedName
          case p@(_: ScCompoundType | _: ScExistentialType) =>
            s"(${innerTypeText(p)})#$escapedName"
          case p =>
            val innerText = innerTypeText(p)
            if (innerText.endsWith(ObjectTypeSuffix)) innerText.stripSuffix("type") + escapedName
            else s"$innerText#$escapedName"
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
        case (s: TermSignature, returnType: ScType) if s.namedElement.isInstanceOf[ScFunction] =>
          val function = s.namedElement.asInstanceOf[ScFunction]
          val substitutor = s.substitutor
          val paramClauses = ScalaTypePresentationUtils.parseParameters(function, -1)(scType => typeText0(substitutor(scType)))
          val retType = if (!compType.equiv(returnType)) typeText0(substitutor(returnType)) else s"this$ObjectTypeSuffix"

          val typeParameters = typeParametersText(function, substitutor)

          Some(s"def ${s.name}$typeParameters$paramClauses: $retType")
        case (s: TermSignature, returnType: ScType) if s.namedElement.isInstanceOf[ScTypedDefinition] =>
          val substitutor = s.substitutor
          val named: Option[ScTypedDefinition] = s.namedElement match {
            case _ if s.paramLength > 0 => None
            case pattern: ScBindingPattern => Some(pattern)
            case fieldId: ScFieldId => Some(fieldId)
            case _ => None
          }

          named.map { typedDefinition =>
            (if (typedDefinition.isVar) "var" else "val") + s" ${typedDefinition.name} : ${typeText0(substitutor(returnType))}"
          }
        case (_: String, signature: TypeAliasSignature) =>
          val alias = signature.typeAlias
          val defnText: String =
            if (signature.isDefinition) s" = ${typeText0(signature.upperBound)}"
            else lowerBoundText(signature.lowerBound)(typeText0) + upperBoundText(signature.upperBound)(typeText0)

          val typeParameters = typeParametersText(alias, signature.substitutor)
          Some(s"type ${signature.name}$typeParameters$defnText")
        case _ => None
      }

      val refinementText = if (declsTexts.isEmpty) Nil else Seq(declsTexts.mkString("{\n  ", "\n\n  ", "\n}"))

      (componentsText ++ refinementText).mkString(" ")
    }

    @tailrec
    def existentialTypeText(existentialType: ScExistentialType, checkWildcard: Boolean, stable: Boolean): String = {
      def existentialArgWithBounds(wildcard: ScExistentialArgument, name: String): String = {
        val argsText = wildcard.typeParameters.map(_.name) match {
          case Seq() => ""
          case parameters => parameters.commaSeparated(model = Model.SquareBrackets)
        }

        val lowerBound = lowerBoundText(wildcard.lower)(innerTypeText(_))
        val upperBound = upperBoundText(wildcard.upper)(innerTypeText(_))
        s"$name$argsText$lowerBound$upperBound"
      }

      def placeholder(wildcard: ScExistentialArgument) =
        existentialArgWithBounds(wildcard, "_")

      def namedExistentials(wildcards: Seq[ScExistentialArgument]) =
        wildcards.map { wildcard =>
          existentialArgWithBounds(wildcard, s"type ${wildcard.name}")
        }.mkString(" forSome {", "; ", "}")

      existentialType match {
        case ScExistentialType(q, Seq(w)) if checkWildcard =>
          if (q == w) placeholder(w)
          else existentialTypeText(existentialType, checkWildcard = false, stable)
        case ScExistentialType(quant @ ParameterizedType(des, typeArgs), wildcards) =>
          val usedMoreThanOnce = ScExistentialArgument.usedMoreThanOnce(quant)

          def mayBePlaceholder(arg: ScExistentialArgument): Boolean =
            !usedMoreThanOnce(arg) && typeArgs.contains(arg) && arg.typeParameters.isEmpty

          val (placeholders, namedWildcards) = wildcards.partition(mayBePlaceholder)

          val prefix = parameterizedTypeText(quant) {
            case arg: ScExistentialArgument  => if (placeholders.contains(arg)) placeholder(arg) else arg.name
            case t                           => innerTypeText(t, needDotType = true, checkWildcard)
          }

          if (namedWildcards.isEmpty) prefix
          else s"($prefix)${namedExistentials(namedWildcards)}"
        case ex: ScExistentialType =>
          s"(${innerTypeText(ex.quantified)})${namedExistentials(ex.wildcards)}"
      }
    }

    object InfixDesignator {
      private[this] val showAsInfixAnnotation: String = "scala.annotation.showAsInfix"

      private def mayUseSimpleName(named: PsiNamedElement): Boolean = {
        val simpleName = named.name
        simpleName == nameRenderer.renderName(named) || context.nameResolvesTo(simpleName, named)
      }

      private def annotated(named: PsiNamedElement) = named match {
        case c: PsiClass => c.getAnnotations.map(_.getQualifiedName).contains(showAsInfixAnnotation)
        case _           => false
      }

      private def hasOperatorName(named: PsiNamedElement): Boolean = ScalaNamesUtil.isOperatorName(named.name)

      def unapply(des: ScType): Option[String] = {
        des.extractDesignated(expandAliases = false)
          .filter(mayUseSimpleName)
          .filter(named => annotated(named) || hasOperatorName(named))
          .map(_.name)
      }
    }

    object TypeLambda {
      def unapply(proj: ScProjectionType): Option[String] = proj match {
        case ScProjectionType.withActual(alias: ScTypeAliasDefinition, _) if alias.kindProjectorPluginEnabled =>
          proj.projected match {
            case ScCompoundType(comps, sigs, aliases) if
            comps.isEmpty && sigs.isEmpty && aliases.contains(alias.name) =>
              Option(KindProjectorSimplifyTypeProjectionInspection.convertToKindProjIectorSyntax(alias))
            case _ => None
          }
        case _ => None
      }
    }

    def parameterizedTypeText(p: ParameterizedType)(printArgsFun: ScType => String): String = p match {
      case ParameterizedType(InfixDesignator(op), Seq(left, right)) =>
        infixTypeText(op, left, right, printArgsFun(_))
      case ParameterizedType(des, typeArgs) =>
        innerTypeText(des) + typeArgs.map(printArgsFun(_)).commaSeparated(model = Model.SquareBrackets)
    }

    def infixTypeText(op: String, left: ScType, right: ScType, printArgsFun: ScType => String): String = {
      val assoc = InfixExpr.associate(op)

      def componentText(`type`: ScType, requiredAssoc: Int) = {
        val needParenthesis = `type` match {
          case ParameterizedType(InfixDesignator(newOp), _) =>
            assoc != InfixExpr.associate(newOp) || assoc == requiredAssoc
          case _ => false
        }

        printArgsFun(`type`).parenthesize(needParenthesis)
      }

      s"${componentText(left, -1)} $op ${componentText(right, 1)}"
    }

    def innerTypeText(t: ScType,
                      needDotType: Boolean = true,
                      checkWildcard: Boolean = false): String = t match {
      case typeParameterType: TypeParameterType if options.expandTypeParameterBounds =>
        typeParameterTypeText(typeParameterType)
      case namedType: NamedType => namedType.name
      case _: WildcardType => "?"
      case ScAbstractType(tpt, _, _) => tpt.name.capitalize + api.ScTypePresentation.ABSTRACT_TYPE_POSTFIX
      case TypeLambda(text)          => text
      case FunctionType(ret, params) if !t.isAliasType =>
        val paramsText = params match {
          case Seq(fun @ FunctionType(_, _)) => innerTypeText(fun).parenthesize()
          case Seq(tup @ TupleType(tps)) => innerTypeText(tup).parenthesize()
          case Seq(head) => innerTypeText(head)
          case _ => typesText(params)
        }
        s"$paramsText ${ScalaPsiUtil.functionArrow} ${innerTypeText(ret)}"
      case ScThisType(element) =>
        val prefix = element match {
          case clazz: ScTypeDefinition => clazz.name + "."
          case _ => ""
        }

        prefix + "this" + typeTail(needDotType)
      case TupleType(comps) =>
        comps match {
          case Seq(head) => s"Tuple1[${innerTypeText(head)}]"
          case _ => typesText(comps)
        }
      case ScDesignatorType(element) =>
        val flag = element match {
          case _: ScObject | _: ScReferencePattern | _: ScParameter => true
          case _ => false
        }

        nameRenderer.renderName(element) + typeTail(flag && needDotType)
      case proj: ScProjectionType if proj != null =>
        projectionTypeText(proj, needDotType)
      case p: ParameterizedType => parameterizedTypeText(p)(innerTypeText(_, checkWildcard = true))
      case JavaArrayType(argument) => s"Array[${innerTypeText(argument)}]"
      case UndefinedType(tpt, _) => "NotInferred" + tpt.name
      case c: ScCompoundType if c != null =>
        compoundTypeText(c)
      case ex: ScExistentialType if ex != null =>
        existentialTypeText(ex, checkWildcard, needDotType)
      case ScTypePolymorphicType(internalType, typeParameters) =>
        typeParameters.map {
          case TypeParameter(parameter, _, lowerType, upperType) =>
            parameter.name + lowerBoundText(lowerType)(_.toString) + upperBoundText(upperType)(_.toString)
        }.commaSeparated(model = Model.SquareBrackets) + " " + internalType.toString
      case mt@ScMethodType(retType, params, _) =>
        implicit val elementScope: ElementScope = mt.elementScope
        innerTypeText(FunctionType(retType, params.map(_.paramType)), needDotType)
      case ScLiteralType(value, _) => value.presentation
      case _ => "" //todo
    }

    innerTypeText(`type`)
  }
}

object ScalaTypePresentation {

  val ObjectTypeSuffix = ".type"
  private val EmptyTuple = (Seq.empty, Seq.empty)
}
