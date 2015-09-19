package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.simulacrum

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScSimpleTypeElement, ScParameterizedTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAssignStmt, ScAnnotation}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.SyntheticMembersInjector
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.SyntheticMembersInjector.Kind
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}

/**
 * @author Alefas
 * @since  17/09/15
 */
class SimulacrumInjection extends SyntheticMembersInjector {
  override def needsCompanionObject(source: ScTypeDefinition): Boolean = {
    source.findAnnotation("simulacrum.typeclass") != null && source.typeParameters.length == 1
  }

  override def injectFunctions(source: ScTypeDefinition): Seq[String] = {
    source match {
      case obj: ScObject =>
        obj.fakeCompanionClassOrCompanionClass match {
          case clazz: ScTypeDefinition if clazz.findAnnotation("simulacrum.typeclass") != null  && clazz.typeParameters.length == 1 =>
            val tpName = clazz.typeParameters.head.name
            Seq(s"def apply[$tpName](implicit instance: ${clazz.name}[$tpName]): ${clazz.name}[$tpName] = instance")
          case _ => Seq.empty
        }
      case _ => Seq.empty
    }
  }

  override def injectInners(source: ScTypeDefinition): Seq[String] = {
    source match {
      case obj: ScObject =>
        ScalaPsiUtil.getCompanionModule(obj) match {
          case Some(clazz) if clazz.findAnnotation("simulacrum.typeclass") != null  && clazz.typeParameters.length == 1 =>
            val tpName = clazz.typeParameters.head.name
            val clazzTpt = new ScTypeParameterType(clazz.typeParameters.head, ScSubstitutor.empty)
            val ops = clazz.functions.flatMap {
              case f: ScFunction =>
                if (f.parameters.headOption.flatMap(_.getType(TypingContext.empty).toOption).
                  exists(_.equiv(clazzTpt))) {
                  val annotation = f.findAnnotation("simulacrum.op")
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
                      val typeParamClasue = f.typeParametersClause.map(_.getText).getOrElse("")
                      val restHeadClause = f.paramClauses.clauses.head.parameters.tail.map(_.getText).mkString(", ")
                      val restClauses = f.paramClauses.clauses.tail.map(_.getText).mkString("")
                      s"def $name$typeParamClasue($restHeadClause)$restClauses: $tpName = ???"
                  }
                } else Seq.empty
            }.mkString("\n  ")
            val className = clazz.name
            val OpsTrait = s"""trait Ops[$tpName] {
                               |  def typeClassInstance: $className[$tpName]
                               |  def self: $tpName
                               |  $ops
                               |}""".stripMargin
            val ToOpsTrait =
              s"""trait To${className}Ops {
                 |  implicit def to${className}Ops[$tpName](target: $tpName)(implicit tc: $className[$tpName]): $className.Ops[$tpName] = ???
                 |}
               """.stripMargin

            val AllOpsSupers = clazz.extendsBlock.templateParents.toSeq.flatMap(parents => parents.typeElements.flatMap {
              case te =>
                te.getType(TypingContext.empty) match {
                  case Success(ScParameterizedType(classType, Seq(tp)), _) if tp.equiv(clazzTpt) =>
                    def fromType: Seq[String] = {
                      ScType.extractClass(classType, Some(clazz.getProject)) match {
                        case Some(cl: ScTypeDefinition) => Seq(s" with ${cl.qualifiedName}.AllOps[$tpName]")
                        case _ => Seq.empty
                      }
                    }
                    //in most cases we have to resolve exactly the same reference
                    //but with .AllOps it will go into companion object
                    (for {
                      ScParameterizedTypeElement(pte, _) <- Option(te)
                      ScSimpleTypeElement(Some(ref)) <- Option(pte)
                    } yield Seq(s" with ${ref.getText}.AllOps[$tpName]")).getOrElse(fromType)
                  case _ => Seq.empty
                }
            }).mkString

            val AllOpsTrait =
              s"""trait AllOps[$tpName] extends $className.Ops[$tpName]$AllOpsSupers {
                 |  def typClassInstance: $className[$tpName]
                 |}
               """.stripMargin
            val opsObject =
              s"""object ops {
                 |  implicit def toAll${className}Ops[$tpName](target: $tpName)(implicit tc: $className[$tpName]): $className.AllOps[$tpName] = ???
                 |}
               """.stripMargin
            Seq(OpsTrait, ToOpsTrait, AllOpsTrait, opsObject)
          case _ => Seq.empty
        }
      case _ => Seq.empty
    }
  }
}
