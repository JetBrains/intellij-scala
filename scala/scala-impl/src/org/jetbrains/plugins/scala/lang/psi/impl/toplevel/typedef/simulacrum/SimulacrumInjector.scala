package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.simulacrum

import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScBooleanLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotation, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScAssignment}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createTypeParameterFromText
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.SyntheticMembersInjector
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.{ParameterizedType, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._

class SimulacrumInjector extends SyntheticMembersInjector {
  import SimulacrumInjector._

  override def needsCompanionObject(source: ScTypeDefinition): Boolean = isSimulacrumTypeclass(source)

  override def injectFunctions(source: ScTypeDefinition): Seq[String] = {
    source match {
      case obj: ScObject =>
        obj.fakeCompanionClassOrCompanionClass match {
          case aClass: ScTypeDefinition if isSimulacrumTypeclass(aClass) =>
            val tpName = aClass.typeParameters.head.name
            val tpText = ScalaPsiUtil.typeParamString(aClass.typeParameters.head)
            Seq(s"@scala.inline def apply[$tpText](implicit instance: ${aClass.name}[$tpName]): ${aClass.name}[$tpName] = instance")
          case _ => Seq.empty
        }
      case _ => Seq.empty
    }
  }

  override def injectInners(source: ScTypeDefinition): Seq[String] = {
    source match {
      case obj: ScObject =>
        ScalaPsiUtil.getCompanionModule(obj) match {
          case Some(aClass) if isSimulacrumTypeclass(aClass) => definitionsToBeInjected(aClass)
          case _                                             => Seq.empty
        }
      case _ => Seq.empty
    }
  }
}

object SimulacrumInjector {
  private[this] val typeclassAnnotation = "simulacrum.typeclass"
  private[this] val noopAnnotation      = "simulacrum.noop"
  private[this] val opAnnotation        = "simulacrum.op"

  private def isSimulacrumTypeclass(source: ScTypeDefinition): Boolean =
    source.findAnnotationNoAliases(typeclassAnnotation) != null && source.typeParameters.length == 1

  /**
    * From scaladocs of simulacrum `op` annotation:
    *
    * Instead of the type class method name being used, the name specified on this annotation is used.
    * If `alias` is true, two methods are generated, one with the original name and one with the
    * specified name.
    *
    */
  private[this] def opsMethodName(sourceMethod: ScFunction): Seq[String] = {
    def extractNamesFromAnnArgs(args: Option[ScArgumentExprList]): Seq[String] = {
      val exprs = args.toSeq.flatMap(_.exprs)

      val alias = exprs.exists {
        case ScBooleanLiteral(isAlias) => isAlias
        case ScAssignment(_, Some(ScBooleanLiteral(isAlias))) => isAlias
        case _                                                => false
      }

      val name = exprs.collectFirst {
        case ScLiteral(opName) => opName
        case ScAssignment(_, Some(ScLiteral(opName))) => opName
      }

      if (alias) Seq(sourceMethod.name) ++ name
      else       name.toSeq
    }

    val supressed = sourceMethod.hasAnnotation(noopAnnotation)

    if (supressed) Seq.empty
    else {
      val opAnn = sourceMethod.findAnnotation(opAnnotation).toOption

      opAnn.fold(Seq(sourceMethod.name)) {
        case scAnn: ScAnnotation => extractNamesFromAnnArgs(scAnn.constructorInvocation.args)
      }
    }
  }

  private[this] def adaptForProperType(m: ScFunction, properTpe: ScTypeParam): Seq[String] = {
    val firstParamType = m.parameters.headOption.flatMap(_.`type`().toOption)

    firstParamType match {
      case Some(TypeParameterType.ofPsi(`properTpe`)) => opsMethodName(m).map(methodText(m, _))
      case _                                          => Seq.empty
    }
  }

  private[this] def adaptForAppliedType(m: ScFunction, tCons: ScTypeParam, liftedTypeParams: Seq[ScTypeParam]): Seq[String] = {
    val firstParamType = m.parameters.headOption.flatMap(_.`type`().toOption)
    val typeParamNames = m.typeParameters.map(tparam => tparam.name -> tparam).toMap

    def extractMethodTypeParameter(tpe: ScType): Boolean = tpe match {
      case TypeParameterType(tp)       => typeParamNames.contains(tp.name)
      case ParameterizedType(_, tArgs) => tArgs.exists(extractMethodTypeParameter)
      case _                           => false
    }

    firstParamType match {
      case Some(ParameterizedType(TypeParameterType.ofPsi(`tCons`), tArgs)) if tArgs.forall(extractMethodTypeParameter) =>
        val nestedTypeArgs = tArgs.collect { case ptpe: ScParameterizedType => ptpe.canonicalText }

        val usedLiftedArgs = tArgs.zipWithIndex.collect {
          case (TypeParameterType(tp), idx) => // do not rewrite nested type arguments, require implicit evidence instead
            val usedTypeParam = typeParamNames(tp.name)
            val liftedParam   = liftedTypeParams(idx)
            usedTypeParam -> TypeParameterType(liftedParam)
        }.toMap

        // generate <:< evidences for nested type args
        val equalityEvidences = nestedTypeArgs.zipWithIndex.map {
          case (typeText, idx) =>
            s"ev$idx: _root_.scala.Predef.<:<[LP$idx, $typeText]"
        }

        opsMethodName(m).map(methodText(m, _, usedLiftedArgs, equalityEvidences))
      case _ => Seq.empty
    }
  }

  private[this] def methodText(
    prototype:            ScFunction,
    name:                 String,
    typeParamsMappings:   Map[ScTypeParam, TypeParameterType] = Map.empty,
    conformanceEvidences: Seq[String]                         = Seq.empty
  ): String = {
    val subst = ScSubstitutor.bind(typeParamsMappings.keys.toList, typeParamsMappings.values.toList)

    def parameterText(p: ScParameter): String =
      p.name + ": " + subst(p.`type`().getOrAny).canonicalText

    def clauseText(p: ScParameterClause): String = {
      val parameters =
        p.parameters.map(parameterText) ++ (if (p.isImplicit) conformanceEvidences else Seq.empty)

      parameters
        .mkString("(" + (if (p.isImplicit) s"implicit " else ""), ", ", ")")
    }

    def conformanceEvidenceClause: String = {
      if (conformanceEvidences.isEmpty) ""
      else                              conformanceEvidences.mkString("(implicit ", ", ", ")")
    }

    val retainedTypeParams = prototype.typeParameters.filterNot(typeParamsMappings.isDefinedAt)

    val typeParamsText =
      if (retainedTypeParams.isEmpty) ""
      else                            s"[${retainedTypeParams.map(ScalaPsiUtil.typeParamString(_)).mkString("", ", ", "")}]"

    val headParams         = prototype.paramClauses.clauses.head.parameters.tail.map(parameterText)
    val restHeadClause     = if (headParams.isEmpty) "" else headParams.mkString("(", ", ", ")")
    val restClauses        = prototype.paramClauses.clauses.tail
    val regularClausesText = restClauses.filterNot(_.isImplicit).map(clauseText).mkString
    val implicitClauseText = restClauses.find(_.isImplicit).fold(conformanceEvidenceClause)(clauseText)
    val returnType         = subst(prototype.returnType.getOrAny).canonicalText

    s"def $name$typeParamsText$restHeadClause$regularClausesText$implicitClauseText: $returnType = ???"
  }

  /**
    * Adapt typeclass methods to be able to use them with an extension syntax.
    *
    * Method should only be adapted if its first parameter in the first parameter list either:
    *   1. Matches typeclass parameter (for proper types)
    *   2. Is of kind F[A0, A1, A2...] where F is a typeclass parameter and A_is are method type parameters
    *
    * e.g. `def foo[A0, A1, B](arg0: F[A0, A1], arg1, ...)` will be adapted to `def foo[B](arg1, ...)`,
    *      references to removed type parameters in argument types are replaced with corresponding lifted type parameters
    *
    * see: [[adaptForProperType]] [[adaptForAppliedType]]
    */
  private[this] def adaptMethods(
    source:           ScTypeDefinition,
    tCons:            ScTypeParam,
    liftedTypeParams: Seq[ScTypeParam],
    proper:           Boolean
  ): Seq[String] = {
    val typeClassMethods = source.functions.filter(isEligibleForAdaptation)

    typeClassMethods.flatMap { m =>
      if (proper) adaptForProperType(m, tCons)
      else        adaptForAppliedType(m, tCons, liftedTypeParams)
    }
  }

  private[this] def isEligibleForAdaptation(f: ScFunction): Boolean =
    !f.getModifierList.accessModifier.exists(mod => mod.isUnqualifiedPrivateOrThis || mod.isProtected)

  private[this] def allOpsSupers(source: ScTypeDefinition, tConsName: String, tParamsText: String): Seq[String] = {
    source.extendsBlock.templateParents.toSeq.flatMap(_.superTypes).flatMap { superTpe =>
      val superFqn = superTpe.extractClass.map(_.getQualifiedName)
      superFqn.map(fqn => s"with $fqn.AllOps[$tConsName$tParamsText]")
    }
  }

  private def definitionsToBeInjected(source: ScTypeDefinition): Seq[String] = {
    val tCons            = source.typeParameters.head
    val liftedTypeParams = tCons.typeParameters.zipWithIndex.map {
      case (_, idx) => createTypeParameterFromText(s"LP$idx")(source)
    }

    val className      = source.name
    val tConsName      = tCons.name
    val tConsText      = ScalaPsiUtil.typeParamString(tCons)
    val isProperType   = liftedTypeParams.isEmpty
    val adaptedMethods = adaptMethods(source, tCons, liftedTypeParams, isProperType).mkString("\n  ")
    val paramNames     = liftedTypeParams.map(_.name)

    val tParamsText =
      if (isProperType) ""
      else              paramNames.mkString(", ", ", ", "")

    val paramsWithBrackets =
      if (isProperType) ""
      else              s"[${paramNames.mkString(", ")}]"

    val opsTrait =
      s"""
         |trait Ops[$tConsText$tParamsText] {
         |  type TypeClassType <: $className[$tConsName]
         |  val typeClassInstance: TypeClassType
         |
         |  def self: $tConsName$paramsWithBrackets
         |
         |  $adaptedMethods
         |}
     """.stripMargin

    val toOpsTrait =
      s"""
         |trait To${className}Ops {
         |  implicit def to${className}Ops[$tConsText$tParamsText](
         |    target: $tConsName$paramsWithBrackets
         |  )(implicit tc: $className[$tConsName]): $className.Ops[$tConsName$tParamsText] = ???
         |}
       """.stripMargin

    val supers = allOpsSupers(source, tConsName, tParamsText).mkString(" ", " ", "")

    val allOpsTrait =
      s"""
         |trait AllOps[$tConsText$tParamsText] extends $className.Ops[$tConsName$tParamsText]$supers {
         |  type TypeClassType <: $className[$tConsName]
         |  val typeClassInstance: TypeClassType
         |}
       """.stripMargin

    val opsObject =
      s"""
         |object ops {
         |  implicit def toAll${className}Ops[$tConsText$tParamsText](
         |    target: $tConsName$paramsWithBrackets
         |  )(implicit tc: $className[$tConsName]): $className.AllOps[$tConsName$tParamsText] = ???
         |}
       """.stripMargin

    val nonInheritedOpsObject =
      s"""
         |object nonInheritedOps extends $className.To${className}Ops
       """.stripMargin

    Seq(opsTrait, toOpsTrait, allOpsTrait, opsObject, nonInheritedOpsObject)
  }
}
