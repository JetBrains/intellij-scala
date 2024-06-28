package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.externalLibraries.kindProjector.inspections.KindProjectorSimplifyTypeProjectionInspection
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType
import org.jetbrains.plugins.scala.lang.parser.parsing.Associativity
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils.operatorAssociativity
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScReferencePattern}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScTypeParametersOwner, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.TypeAnnotationRenderer.ParameterTypeDecorator
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.TypePresentation.PresentationOptions
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.{ElementScope, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings.{getInstance => ScalaApplicationSettings}

import scala.annotation.tailrec

trait ScalaTypePresentation extends TypePresentation {
  typeSystem: api.TypeSystem =>

  import ScalaTypePresentation._

  override def typeText(
    `type`: ScType,
    nameRenderer: NameRenderer,
    options: PresentationOptions
  )(implicit context: TypePresentationContext): String = {
    val textEscaper: TextEscaper = nameRenderer
    val boundsRenderer = new TypeBoundsRenderer(textEscaper)

    def typesText(types: Iterable[ScType]): String = types
      .map(innerTypeText(_))
      .commaSeparated(model = Model.Parentheses)

    def typeTail(need: Boolean) = if (need) ObjectTypeSuffix else ""

    def typeParametersText(paramsOwner: ScTypeParametersOwner, substitutor: ScSubstitutor): String = paramsOwner.typeParameters match {
      case Seq()  => ""
      case params => params.map(typeParamText(_, substitutor)).commaSeparated(model = Model.SquareBrackets)
    }

    def typeParamText(param: ScTypeParam, substitutor: ScSubstitutor): String = {
      val typeRenderer: TypeRenderer = t => typeText(substitutor(t), nameRenderer, options)
      val typeParamsRenderer = new TypeParamsRenderer(typeRenderer, boundsRenderer)
      typeParamsRenderer.render(param)
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

      if (context.nameResolvesTo(refName + typeTailForProjection, e))
        // if the reference can be resolved from the context, we do not render a redundant context prefix
        escapedName + typeTailForProjection
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

      val componentsText = if (comps.isEmpty || comps == Seq(projectContext.stdTypes.AnyRef)) Nil else Seq(comps.map {
        case tp@FunctionType(_, _) => "(" + innerTypeText(tp) + ")"
        case tp => innerTypeText(tp)
      }.mkString(context.compoundTypeSeparatorText))

      val declsTexts = (signatureMap ++ typeMap).flatMap {
        case (s: TermSignature, returnType: ScType) if s.namedElement.is[ScFunction] =>
          val function = s.namedElement.asInstanceOf[ScFunction]
          val substitutor = s.substitutor

          val paramClauses: String = {
            val typeRenderer: TypeRenderer = t => typeText0(substitutor(t))
            val paramRenderer = new ParameterRenderer(
              typeRenderer,
              ModifiersRenderer.SimpleText(textEscaper),
              new TypeAnnotationRenderer(typeRenderer, ParameterTypeDecorator.DecorateAll),
              textEscaper,
              withMemberModifiers = false,
              withAnnotations = true
            )
            val paramsRenderer = new ParametersRenderer(
              paramRenderer,
              shouldRenderImplicitModifier = true
            )
            paramsRenderer.renderClauses(function)
          }

          val retType = if (!compType.equiv(returnType)) typeText0(substitutor(returnType)) else s"this$ObjectTypeSuffix"

          val typeParameters = typeParametersText(function, substitutor)

          Some(s"def ${s.name}$typeParameters$paramClauses: $retType")
        case (s: TermSignature, returnType: ScType) if s.namedElement.is[ScTypedDefinition] =>
          val substitutor = s.substitutor
          val named: Option[ScTypedDefinition] = s.namedElement match {
            case _ if s.paramLength > 0 => None
            case pattern: ScBindingPattern => Some(pattern)
            case fieldId: ScFieldId => Some(fieldId)
            case _ => None
          }

          named.map { typedDefinition =>
            (if (typedDefinition.isVar) "var" else "val") + s" ${typedDefinition.name}${if (typedDefinition.name.lastOption.exists(c => !c.isLetterOrDigit && c != '`')) " " else ""}: ${typeText0(substitutor(returnType))}"
          }
        case (_: String, signature: TypeAliasSignature) =>
          val alias = signature.typeAlias
          val defnText: String =
            if (signature.isDefinition) s" = ${typeText0(signature.upperBound)}"
            else boundsRenderer.lowerBoundText(signature.lowerBound)(typeText0) + boundsRenderer.upperBoundText(signature.upperBound)(typeText0)

          val typeParameters = typeParametersText(alias, signature.substitutor)
          Some(s"type ${signature.name}$typeParameters$defnText")
        case _ => None
      }

      val refinementText = if (declsTexts.isEmpty) Nil else Seq(declsTexts.mkString("{ ", "; ", " }"))

      (componentsText ++ refinementText).mkString(" ")
    }

    @tailrec
    def existentialTypeText(existentialType: ScExistentialType, checkWildcard: Boolean, stable: Boolean): String = {
      def existentialArgWithBounds(wildcard: ScExistentialArgument, name: String): String = {
        val argsText = wildcard.typeParameters.map(_.name) match {
          case Seq() => ""
          case parameters => parameters.commaSeparated(model = Model.SquareBrackets)
        }

        val lowerBound = boundsRenderer.lowerBoundText(wildcard.lower)(innerTypeText(_))
        val upperBound = boundsRenderer.upperBoundText(wildcard.upper)(innerTypeText(_))
        s"$name$argsText$lowerBound$upperBound"
      }

      def placeholder(wildcard: ScExistentialArgument) =
        existentialArgWithBounds(wildcard, if (context.compoundTypeWithAndToken) "?" else "_")

      def namedExistentials(wildcards: Seq[ScExistentialArgument]) =
        wildcards.map { wildcard =>
          existentialArgWithBounds(wildcard, s"type ${wildcard.name}")
        }.mkString(" forSome {", "; ", "}")

      existentialType match {
        case ScExistentialType(q, Seq(w)) if checkWildcard =>
          if (q == w) placeholder(w)
          else existentialTypeText(existentialType, checkWildcard = false, stable)
        case ScExistentialType(quant @ ParameterizedType(_, typeArgs), wildcards) =>
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

      def unapply(des: ScType): Option[PsiNamedElement] = {
        des.extractDesignated(expandAliases = false)
          .filter(mayUseSimpleName)
          .filter(named => annotated(named) || hasOperatorName(named))
      }
    }

    object TypeLambda {
      def unapply(proj: ScProjectionType): Option[String] = proj match {
        case ScProjectionType.withActual(alias: ScTypeAliasDefinition, _) if alias.kindProjectorPluginEnabled =>
          proj.projected match {
            case ScCompoundType(comps, sigs, aliases) if
              comps == Seq(projectContext.stdTypes.AnyRef) && sigs.isEmpty && aliases.contains(alias.name) =>
              Option(KindProjectorSimplifyTypeProjectionInspection.convertToKindProjectorSyntax(alias))
            case _ => None
          }
        case _ => None
      }
    }

    def parameterizedTypeText(p: ParameterizedType)(printArgsFun: ScType => String): String = p match {
      case ParameterizedType(InfixDesignator(op), Seq(left, right)) if !ScalaApplicationSettings.PRECISE_TEXT => // SCL-21179
        infixTypeText(op, left, right, printArgsFun(_))
      case ParameterizedType(des, typeArgs) =>
        innerTypeText(des) + typeArgs.map(printArgsFun(_)).commaSeparated(model = Model.SquareBrackets)
    }

    def infixTypeText(op: PsiNamedElement, left: ScType, right: ScType, printArgsFun: ScType => String): String = {
      val assoc = operatorAssociativity(op.name)

      def componentText(`type`: ScType, requiredAssoc: Associativity.LeftOrRight) = {
        val needParenthesis = `type` match {
          case ParameterizedType(InfixDesignator(newOp), _) =>
            assoc != operatorAssociativity(newOp.name) || assoc == requiredAssoc
          case _ => false
        }

        printArgsFun(`type`).parenthesize(needParenthesis)
      }

      val opRendered =
        if (options.renderInfixType) nameRenderer.renderName(op)
        else nameRenderer.escapeName(op.name)
      s"${componentText(left, Associativity.Right)} $opRendered ${componentText(right, Associativity.Left)}"
    }

    def textOf(params: Seq[ScType]) = params match {
      case Seq(fun@FunctionType(_, _)) => innerTypeText(fun).parenthesize()
      case Seq(tup@TupleType(_)) => innerTypeText(tup).parenthesize()
      case Seq(mt: ScMatchType) => innerTypeText(mt).parenthesize()
      case Seq(head) => innerTypeText(head)
      case _ => typesText(params)
    }

    def innerTypeText(
      t: ScType,
      needDotType: Boolean = true,
      checkWildcard: Boolean = false
    ): String = t match {
      case stdType: StdType if options.renderStdTypes =>
        stdType.extractClass match {
          case Some(clazz) => nameRenderer.renderName(clazz)
          case _           => nameRenderer.escapeName(stdType.name)
        }
      case namedType: NamedType => namedType.name
      case _: WildcardType => "?"
      case ScAbstractType(tpt, _, _) => tpt.name.capitalize + api.presentation.TypePresentation.ABSTRACT_TYPE_POSTFIX
      case TypeLambda(text)          => text
      case FunctionType(ret, params) if !t.isAliasType =>
        val paramsText = textOf(params)
        s"$paramsText ${ScalaPsiUtil.functionArrow} ${innerTypeText(ret)}"
      case ContextFunctionType(ret, params) if !t.isAliasType =>
        val paramsText = textOf(params)
        s"$paramsText ${ScalaPsiUtil.contextFunctionArrow} ${innerTypeText(ret)}"
      case ScThisType(element) =>
        val prefix = element match {
          case clazz: ScTypeDefinition => clazz.name + "."
          case _ => ""
        }

        prefix + "this" + typeTail(needDotType)
      case TupleType(comps) if !ScalaApplicationSettings.PRECISE_TEXT || !t.isAliasType => // SCL-21175
        comps match {
          case Seq(head) => (if (ScalaApplicationSettings.PRECISE_TEXT && options.canonicalForm) "_root_.scala." else "") + s"Tuple1[${innerTypeText(head)}]" // SCL-21183
          case _ => typesText(comps)
        }
      case ScDesignatorType(element) =>
        val flag = element match {
          case _: ScObject | _: ScReferencePattern | _: ScParameter => true
          case _ => false
        }

        nameRenderer.renderName(element) + typeTail(flag && needDotType)
      case proj: ScProjectionType =>
        projectionTypeText(proj, needDotType)
      case p: ParameterizedType =>
        parameterizedTypeText(p)(innerTypeText(_, checkWildcard = true))
      case JavaArrayType(argument) => (if (ScalaApplicationSettings.PRECISE_TEXT && options.canonicalForm) "_root_.scala." else "") + s"Array[${innerTypeText(argument)}]" // SCL-21183
      case UndefinedType(tpt, _) => "NotInferred" + tpt.name
      case ScAndType(lhs, rhs) =>
        val l = innerTypeText(lhs)
        val r = innerTypeText(rhs)
        if (ScalaApplicationSettings.PRECISE_TEXT && options.canonicalForm) "_root_.scala.&[" + l + ", " + r + "]" else l + " & " + r // SCL-21559
      case ScOrType(lhs, rhs) =>
        val l = innerTypeText(lhs)
        val r = innerTypeText(rhs)
        if (ScalaApplicationSettings.PRECISE_TEXT && options.canonicalForm) "_root_.scala.|[" + l + ", " + r + "]" else l + " | " + r // SCL-21559
      case c: ScCompoundType =>
        compoundTypeText(c)
      case ex: ScExistentialType =>
        existentialTypeText(ex, checkWildcard, needDotType)
      case pt@ScTypePolymorphicType(internalType, typeParameters) =>
        def toString(tpe: ScType) = if (ScalaApplicationSettings.PRECISE_TEXT) innerTypeText(tpe) else tpe.toString // SCL-21203
        val typeParametersTexts = typeParameters.map {
          case TypeParameter(parameter, _, lowerType, upperType) =>
            parameter.name + boundsRenderer.lowerBoundText(lowerType)(toString) + boundsRenderer.upperBoundText(upperType)(toString)
        }
        val typeParametersText = typeParametersTexts.commaSeparated(model = Model.SquareBrackets)
        // TODO Custom lambda and polymorphic function types, SCL-20394
        val separator = if (pt.isLambdaTypeElement) TypeLambdaArrowWithSpaces else if (FunctionType.isFunctionType(internalType)) s" ${ScalaPsiUtil.functionArrow} " else " "
        typeParametersText + separator + toString(internalType)
      case mt@ScMethodType(retType, params, _) =>
        implicit val elementScope: ElementScope = mt.elementScope
        innerTypeText(FunctionType(retType, params.map(_.paramType)), needDotType)
      case ScLiteralType(value, _) =>
        value.presentation
      case ScMatchType(scrutinee, cases) =>
        val scrutineeText = innerTypeText(scrutinee)
        val caseTexts = cases.map(cs => "case " + cs._1 + " => " + cs._2)
        val parenthesesRequired = scrutinee.is[ScMatchType] || FunctionType.isFunctionType(scrutinee)
        (if (parenthesesRequired) scrutineeText.parenthesize() else scrutineeText)  + " match " + caseTexts.mkString("{ ", "; ", " }")
      case _ => "" //todo
    }

    innerTypeText(`type`)
  }
}

object ScalaTypePresentation {
  val ObjectTypeSuffix = ".type"

  private val TypeLambdaArrowWithSpaces = s" ${ScalaTokenType.TypeLambdaArrow} "
}
