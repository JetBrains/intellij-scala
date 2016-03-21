package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.simulacrum

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParameterizedTypeElement, ScSimpleTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAnnotation, ScAssignStmt}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.SyntheticMembersInjector
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}
import org.jetbrains.plugins.scala.project.ProjectExt

/**
 * @author Alefas
 * @since  17/09/15
 */
class SimulacrumInjection extends SyntheticMembersInjector {
  override def needsCompanionObject(source: ScTypeDefinition): Boolean = {
    source.findAnnotationNoAliases("simulacrum.typeclass") != null && source.typeParameters.length == 1
  }

  override def injectFunctions(source: ScTypeDefinition): Seq[String] = {
    source match {
      case obj: ScObject =>
        obj.fakeCompanionClassOrCompanionClass match {
          case clazz: ScTypeDefinition if clazz.findAnnotationNoAliases("simulacrum.typeclass") != null  && clazz.typeParameters.length == 1 =>
            val tpName = clazz.typeParameters.head.name

            val tpText = ScalaPsiUtil.typeParamString(clazz.typeParameters.head)
            Seq(s"def apply[$tpText](implicit instance: ${clazz.name}[$tpName]): ${clazz.name}[$tpName] = instance")
          case _ => Seq.empty
        }
      case _ => Seq.empty
    }
  }

  override def injectInners(source: ScTypeDefinition): Seq[String] = {
    source match {
      case obj: ScObject =>
        ScalaPsiUtil.getCompanionModule(obj) match {
          case Some(clazz) if clazz.findAnnotationNoAliases("simulacrum.typeclass") != null  && clazz.typeParameters.length == 1 =>
            val clazzTypeParam = clazz.typeParameters.head
            val tpName = clazzTypeParam.name
            val tpText = ScalaPsiUtil.typeParamString(clazzTypeParam)
            val tpAdditional = if (clazzTypeParam.typeParameters.nonEmpty) Some(s"Lifted$tpName") else None
            val additionalWithComma = tpAdditional.map(", " + _).getOrElse("")
            val additionalWithBracket = tpAdditional.map("[" + _ + "]").getOrElse("")
            def isProperTpt(tp: ScType): Option[Option[ScTypeParameterType]] = {
              tp match {
                case ScTypeParameterType(_, _, _, _, param) if param == clazzTypeParam => Some(None)
                case ScParameterizedType(ScTypeParameterType(_, _, _, _, param),
                  Seq(p: ScTypeParameterType)) if param == clazzTypeParam => Some(Some(p))
                case _ => None
              }
            }
            val ops = clazz.functions.flatMap {
              case f: ScFunction =>
                f.parameters.headOption.flatMap(_.getType(TypingContext.empty).toOption).flatMap(tp => isProperTpt(tp)) match {
                  case Some(funTypeParamToLift) =>
                    val annotation = f.findAnnotationNoAliases("simulacrum.op")
                    val names =
                      annotation match {
                        case a: ScAnnotation =>
                          a.constructor.args match {
                            case Some(args) =>
                              args.exprs.headOption match {
                                case Some(l: ScLiteral) if l.isString =>
                                  l.getValue match {
                                    case value: String =>
                                      args.exprs match {
                                        case Seq(_, second) =>
                                          second match {
                                            case l: ScLiteral if l.getValue == true => Seq(value, f.name)
                                            case a: ScAssignStmt =>
                                              a.getRExpression match {
                                                case Some(l: ScLiteral) if l.getValue == true => Seq(value, f.name)
                                                case _ => Seq(value)
                                              }
                                            case _ => Seq(value)
                                          }
                                        case _ => Seq(value)
                                      }
                                    case _ => Seq(f.name)
                                  }
                                case _ => Seq(f.name)
                              }
                            case None => Seq(f.name)
                          }
                        case _ => Seq(f.name)
                      }
                    names.map {
                      case name =>
                        val substOpt = funTypeParamToLift match {
                          case Some(typeParam) if tpAdditional.nonEmpty =>
                            val subst = ScSubstitutor.empty.bindT((typeParam.name, typeParam.getId),
                              new ScTypeParameterType(
                                ScalaPsiElementFactory.createTypeParameterFromText(tpAdditional.get, source.getManager), ScSubstitutor.empty
                              ))
                            Some(subst)
                          case _ => None
                        }
                        def paramText(p: ScParameter): String = {
                          substOpt match {
                            case Some(subst) =>
                              p.name + " : " + subst.subst(p.getType(TypingContext.empty).getOrAny).canonicalText
                            case _ => p.getText
                          }
                        }
                        def clauseText(p: ScParameterClause): String = {
                          p.parameters.map(paramText).mkString("(" + (if (p.isImplicit) "implicit " else ""), ", ", ")")
                        }
                        val typeParamClasue = f.typeParametersClause.map(_.getText).getOrElse("")
                        val headParams = f.paramClauses.clauses.head.parameters.tail.map(paramText)
                        val restHeadClause = if (headParams.isEmpty) "" else headParams.mkString("(", ", ", ")")
                        val restClauses = f.paramClauses.clauses.tail.map(clauseText).mkString("")
                        val rt = substOpt match {
                          case Some(subst) => subst.subst(f.returnType.getOrAny).canonicalText
                          case None => f.returnType.getOrAny.canonicalText
                        }
                        s"def $name$typeParamClasue$restHeadClause$restClauses: $rt = ???"
                    }
                  case _ => Seq.empty
                }
            }.mkString("\n  ")
            val className = clazz.name
            val OpsTrait = s"""trait Ops[$tpText$additionalWithComma] {
                               |  def typeClassInstance: $className[$tpName]
                               |  def self: $tpName$additionalWithBracket
                               |  $ops
                               |}""".stripMargin
            val ToOpsTrait =
              s"""trait To${className}Ops {
                 |  implicit def to${className}Ops[$tpText$additionalWithComma](target: $tpName$additionalWithBracket)(implicit tc: $className[$tpName]): $className.Ops[$tpName$additionalWithComma] = ???
                 |}
               """.stripMargin

            val AllOpsSupers = clazz.extendsBlock.templateParents.toSeq.flatMap(parents => parents.typeElements.flatMap {
              case te =>
                te.getType(TypingContext.empty) match {
                  case Success(ScParameterizedType(classType, Seq(tp)), _) if isProperTpt(tp).isDefined =>
                    def fromType: Seq[String] = {
                      val project = clazz.getProject
                      implicit val typeSystem = project.typeSystem
                      classType.extractClass(project) match {
                        case Some(cl: ScTypeDefinition) => Seq(s" with ${cl.qualifiedName}.AllOps[$tpName$additionalWithComma]")
                        case _ => Seq.empty
                      }
                    }
                    //in most cases we have to resolve exactly the same reference
                    //but with .AllOps it will go into companion object
                    (for {
                      ScParameterizedTypeElement(pte, _) <- Option(te)
                      ScSimpleTypeElement(Some(ref)) <- Option(pte)
                    } yield Seq(s" with ${ref.getText}.AllOps[$tpName$additionalWithComma]")).getOrElse(fromType)
                  case _ => Seq.empty
                }
            }).mkString

            val AllOpsTrait =
              s"""trait AllOps[$tpText$additionalWithComma] extends $className.Ops[$tpName$additionalWithComma]$AllOpsSupers {
                 |  def typClassInstance: $className[$tpName]
                 |}
               """.stripMargin
            val opsObject =
              s"""object ops {
                 |  implicit def toAll${className}Ops[$tpText$additionalWithComma](target: $tpName$additionalWithBracket)(implicit tc: $className[$tpName]): $className.AllOps[$tpName$additionalWithComma] = ???
                 |}
               """.stripMargin
              Seq(OpsTrait, ToOpsTrait, AllOpsTrait, opsObject)
          case _ => Seq.empty
        }
      case _ => Seq.empty
    }
  }
}
