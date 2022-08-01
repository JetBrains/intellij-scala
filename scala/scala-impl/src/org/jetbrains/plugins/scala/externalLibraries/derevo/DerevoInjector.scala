package org.jetbrains.plugins.scala.externalLibraries.derevo

import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.externalLibraries.derevo.DerevoInjector._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.SyntheticMembersInjector
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameterType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.{ScCompoundType, ScParameterizedType, ScType}
import org.jetbrains.plugins.scala.project.ProjectContext

class DerevoInjector extends SyntheticMembersInjector {
  val derevoDerive = "derevo.derive"
  val derevoComposite = "derevo.composite"

  def extractAnnotParams(derivationObject: ScObject): Option[DeriveAnnotParams] = {
    val supertypes = derivationObject.extendsBlock.superTypes
    val keepRefinements = supertypes.exists {
      case d: ScDesignatorType =>
        d.extractDesignated(true).exists {
          case t: ScTrait => t.qualifiedName == "derevo.KeepRefinements"; case _ => false
        }
      case _ =>
        false
    }

    def typeArgToTc(typearg: ScType): Option[TypeConstructor] = typearg match {
      case d: ScDesignatorType =>
        d.extractDesignated(false).flatMap {
          case t: ScTemplateDefinition =>
            Some(new TypeConstructor {
              override def apply(tpe: String): String = s"_root_.${t.qualifiedName}[$tpe]"

              override def qualifiedName: String = "_root_." + t.qualifiedName
            })
          case _ => None
        }
      case ScProjectionType(c: ScCompoundType, elem) =>
        for {
          t <- c.typesMap.get(elem.name)
          if t.typeParams.size == 1
          d          <- t.typeAlias.asOptionOf[ScTypeAliasDefinition]
          p          <- d.aliasedType.toOption.filterByType[ScParameterizedType]
          designated <- p.extractDesignated(expandAliases = true).filterByType[ScTypeDefinition]
        } yield new TypeConstructor {
          override def apply(tpe: String): String = {
            val fixedTpeArgs = p.typeArguments.map {
              case _: TypeParameterType => tpe
              case typeArg              => typeArg.canonicalText
            }

            s"_root_.${designated.qualifiedName}[${fixedTpeArgs.mkString(", ")}]"
          }

          override def qualifiedName: String = "_root_." + designated.qualifiedName
        }
      case tpe =>
        tpe.extractDesignated(true).flatMap {
          case t: ScTrait => typeArgToTc(ScDesignatorType(t))
          case _          => None
        }
    }

    supertypes.collect { case s: ScParameterizedType => s.extractDesignated(true) -> s.typeArguments }.collectFirst {
      case (Some(t: ScTrait), Seq(typearg)) if t.qualifiedName == "derevo.Derivation" =>
        typeArgToTc(typearg).map(x => DeriveAnnotParams(keepRefinements, x, x))
      case (Some(t: ScTrait), Seq(from, to, _)) if t.qualifiedName == "derevo.SpecificDerivation" =>
        typeArgToTc(from).zip(typeArgToTc(to)).map { case (fromTc, toTc) =>
          DeriveAnnotParams(keepRefinements, fromTc, toTc)
        }
    }.flatten
  }

  def extractTypeclassTpeFromExpr(expr: ScExpression, methodCallResultTpe: Option[ScType]): IterableOnce[(TypeConstructor, TypeConstructor)] = {
    def tcForParameterizedType(a: ScParameterizedType, to: TypeConstructor): TypeConstructor = new TypeConstructor {
      override def apply(tpe: String): String = s"${a.designator.canonicalText}[$tpe, ${a.typeArguments.drop(1).map(_.canonicalText).mkString(", ")}]"
      override def qualifiedName: String = to.qualifiedName
    }

    def tcForCompoundType(c: ScCompoundType, firstComponent: ScParameterizedType, to: TypeConstructor): TypeConstructor = new TypeConstructor {
      override def apply(tpe: String): String = {
        val fc = s"${firstComponent.designator.canonicalText}[$tpe, ${firstComponent.typeArguments.drop(1).map(_.canonicalText).mkString(", ")}]"

        implicit val ctx: ProjectContext = c.projectContext

        c.components.size match {
          case 0 | 1 => fc + " " + c.copy(components = Nil).canonicalText
          case _ => fc + " with " + c.copy(components = c.components.drop(1)).canonicalText
        }
      }

      override def qualifiedName: String = to.qualifiedName
    }

    def extractTcsFromDefDef(f: ScFunction): Option[(TypeConstructor, TypeConstructor)] = f.containingClass match {
      case o: ScObject =>
        extractAnnotParams(o).flatMap {
          case DeriveAnnotParams(true, _, to) =>
            methodCallResultTpe.orElse(f.returnType.toOption) match {
              case Some(a: ScParameterizedType) =>
                val ktc = tcForParameterizedType(a, to)
                Some(ktc -> ktc)
              case Some(a: ScCompoundType) =>
                a.components.headOption.collect {
                  case p: ScParameterizedType =>
                    val ktc = tcForCompoundType(a, p, to)
                    ktc -> ktc
                }
              case _ => None
            }
          case DeriveAnnotParams(_, from, to) =>
            Some(from -> to)
        }
      case _ => None
    }

    def checkParamClauses(f: ScFunction) =
      f.paramClauses.clauses.forall(c => c.isImplicit || c.parameters.isEmpty)

    expr match {
      case r: ScReferenceExpression =>
        val resolvedShapes = r.shapeResolve

        if (resolvedShapes.length > 0) {
          resolvedShapes(0).element match {
            case o: ScObject =>
              def fn(name: String) = o.members.collectFirst {
                case f: ScFunction if f.name == name && checkParamClauses(f) => f
              }

              fn("instance").orElse(fn("apply")) match {
                case Some(fn) => extractTcsFromDefDef(fn)
                case None => o.extendsBlock.superTypes.flatMap {
                  case cdt: ScDesignatorType if cdt.canonicalText == "_root_.derevo.CompositeDerivation" =>
                    extractTypeclassTpesFromClass(o, derevoComposite)
                  case _ => Nil
                }
              }
            case f: ScFunction => extractTcsFromDefDef(f)
            case _             => Nil
          }
        } else Nil
      case _ => Nil
    }
  }

  def extractTypeclassTpesFromClass(source: PsiClass, qualifiedName: String): Seq[(TypeConstructor, TypeConstructor)] =
    deriveAnnotArgs(source, qualifiedName).flatMap {
      case r: ScReferenceExpression =>
        extractTypeclassTpeFromExpr(r, None)
      case m: ScMethodCall =>
        m.firstChild
          .collect { case s: ScReferenceExpression => s }
          .toList
          .flatMap(extractTypeclassTpeFromExpr(_, m.`type`().toOption))
      case _ => None
    }

  def generateImplicitForTypeclassTpes(ix: Int, obj: ScObject, tpeFrom: TypeConstructor, tpeTo: TypeConstructor): String = {
    val tparams = obj.fakeCompanionClassOrCompanionClass.asInstanceOf[ScTypeDefinition].typeParameters

    if (tparams.nonEmpty) {
      val tparamStr = tparams.map(_.name).mkString(", ")

      val implicits = tparams.zipWithIndex.flatMap { case (tp, idx) =>
        tp.findAnnotationNoAliases("derevo.phantom") match {
          case _: ScAnnotation => None
          case _ => Some(s"ev$idx: ${tpeFrom.apply(tp.name)}")
        }
      }

      val implicitStr = if (implicits.isEmpty) "" else s"(implicit ${implicits.mkString(", ")})"

      s"implicit def idea$$derevo_injected_$ix[$tparamStr]$implicitStr: ${tpeTo(s"${obj.name}[$tparamStr]")} = ???"
    } else {
      s"implicit val idea$$derevo_injected_$ix: ${tpeTo(obj.name)} = ???"
    }
  }

  def deriveAnnotArgs(source: PsiClass, qualifiedName: String): Seq[ScExpression] = source match {
    case tpe: ScTypeDefinition =>
      tpe.findAnnotationNoAliases(qualifiedName) match {
        case annot: ScAnnotation =>
          annot.annotationExpr.getAnnotationParameters
        case _ => Nil
      }
    case _ => Nil
  }

  override def needsCompanionObject(source: ScTypeDefinition): Boolean = deriveAnnotArgs(source, derevoDerive).nonEmpty

  override def injectMembers(source: ScTypeDefinition): Seq[String] = source match {
    case o: ScObject =>
      extractTypeclassTpesFromClass(o.fakeCompanionClassOrCompanionClass, derevoDerive)
        .zipWithIndex
        .map { case ((tpeFrom, tpeTo), ix) => generateImplicitForTypeclassTpes(ix, o, tpeFrom, tpeTo) }
    case _ => Nil
  }
}

object DerevoInjector {
  trait TypeConstructor {
    def apply(tpe: String): String
    def qualifiedName: String
  }

  case class DeriveAnnotParams(
    keepRefinements: Boolean,
    tFrom: TypeConstructor,
    tTo: TypeConstructor
  )
}
