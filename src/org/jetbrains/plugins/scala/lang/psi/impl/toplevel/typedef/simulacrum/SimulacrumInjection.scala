package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.simulacrum

import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.SyntheticMembersInjector
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.SyntheticMembersInjector.Kind
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext

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
        obj.fakeCompanionClassOrCompanionClass match {
          case clazz: ScTypeDefinition if clazz.findAnnotation("simulacrum.typeclass") != null  && clazz.typeParameters.length == 1 =>
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
                                    Seq(value) //todo: alias
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

            val AllOpsSupers = clazz.superTypes.flatMap {
              case ScParameterizedType(classType, Seq(tp)) if tp.equiv(clazzTpt) =>
                ScType.extractClass(classType, Some(clazz.getProject)) match {
                  case Some(cl: ScTypeDefinition) => Seq(s" with ${cl.qualifiedName}.AllOps[$tpName]") //todo: what about inner classes?
                  case _ => Seq.empty
                }
              case _ => Seq.empty
            }.mkString

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
