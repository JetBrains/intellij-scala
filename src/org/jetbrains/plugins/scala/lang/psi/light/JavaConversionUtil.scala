package org.jetbrains.plugins.scala.lang.psi.light

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScAnnotationsHolder
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScClassParents
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}

/**
 * User: Alefas
 * Date: 18.02.12
 */

object JavaConversionUtil {
  def typeText(tp: ScType, project: Project, scope: GlobalSearchScope): String = {
    val psiType = ScType.toPsi(tp, project, scope)
    psiType.getCanonicalText
  }

  def modifiers(s: ScModifierListOwner, isStatic: Boolean): String = {
    val builder = new StringBuilder

    s match {
      case holder: ScAnnotationsHolder =>
        val keywordAnnotations = Map("scala.native" -> "native", "scala.annotation.strictfp" -> "strictfp",
          "scala.volatile" -> "volatile", "scala.transient" -> "transient")
        for (annotation <- holder.annotations if !keywordAnnotations.values.toSet.contains(annotation.getQualifiedName) &&
             annotation.getQualifiedName != "scala.throws") {
          builder.append("@").append(annotation.getQualifiedName)
          annotation.constructor.args match {
            case Some(args) =>
              def convertArgs(args: Seq[ScExpression]): String = {
                def convertExpression(e: ScExpression): String = {
                  def problem = "CannotConvertExpression"
                  e match {
                    case a: ScAssignStmt =>
                      val res = a.getLExpression.getText + " = "
                      a.getRExpression match {
                        case Some(expr) => res + convertExpression(expr)
                        case _ => res
                      }
                    case l: ScLiteral if !l.isMultiLineString => l.getText
                    case l: ScLiteral => "\"" + StringUtil.escapeStringCharacters(l.getValue.toString) + "\""
                    case call: ScMethodCall =>
                      if (call.getInvokedExpr.getText.endsWith("Array")) {
                        call.args.exprs.map(convertExpression(_)).mkString("{", ", ", "}")
                      } else problem
                    case call: ScGenericCall =>
                      if (call.referencedExpr.getText.endsWith("classOf")) {
                        val arguments = call.arguments
                        if (arguments.length == 1) {
                          val tp = arguments.apply(0).getType(TypingContext.empty)
                          tp match {
                            case Success(tp, _) =>
                              ScType.extractClass(tp, Some(s.getProject)) match {
                                case Some(clazz) => clazz.getQualifiedName + ".class"
                                case _ => problem
                              }
                            case _ => problem
                          }
                        } else problem
                      } else problem
                    case n: ScNewTemplateDefinition =>
                      n.extendsBlock.templateParents match {
                        case Some(c: ScClassParents) =>
                          c.constructor match {
                            case Some(constr) =>
                              constr.reference match {
                                case Some(ref) =>
                                  ref.resolve() match {
                                    case c: PsiClass =>
                                      var res = "@" + c.getQualifiedName
                                      constr.args match {
                                        case Some(args) => res += convertArgs(args.exprs)
                                        case _ =>
                                      }
                                      res
                                    case _ => problem
                                  }
                                case _ => problem
                              }
                            case _ => problem
                          }
                        case _ => problem
                      }
                    case _ => problem
                  }
                }
                args.map(convertExpression(_)).mkString("(", ", ", ")")
              }
              builder.append(convertArgs(args.exprs))
            case _ =>
          }
          builder.append("\n")
        }
        for (annotation <- keywordAnnotations) {
          if (holder.hasAnnotation(annotation._1) != None) builder.append(annotation._2).append(" ")
        }
      case _ =>
    }

    if (isStatic) {
      builder.append("static ")
    }

    if (s.hasModifierProperty("final")) {
      builder.append("final ")
    }

    s.getModifierList.accessModifier match {
      case Some(a) if a.isUnqualifiedPrivateOrThis => builder.append("private ")
      case _ => builder.append("public ")
    }

    builder.toString()
  }
}
