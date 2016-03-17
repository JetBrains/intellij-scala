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
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt}

/**
 * User: Alefas
 * Date: 18.02.12
 */

object JavaConversionUtil {

  val keywordAnnotations = Map(
    "scala.native" -> "native",
    "scala.annotation.strictfp" -> "strictfp",
    "scala.volatile" -> "volatile",
    "scala.transient" -> "transient")

  def typeText(tp: ScType, project: Project, scope: GlobalSearchScope): String = {
    tp.toPsiType(project, scope).getCanonicalText
  }

  def annotationsAndModifiers(s: ScModifierListOwner, isStatic: Boolean): String = {
    val builder = new StringBuilder

    s match {
      case holder: ScAnnotationsHolder =>
        val annotationsText = annotations(holder).mkString("\n")
        if (!annotationsText.isEmpty)
          builder.append(annotationsText).append(" ")
        for ((fqn, keyword) <- keywordAnnotations) {
          if (holder.hasAnnotation(fqn).isDefined) builder.append(keyword).append(" ")
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
  
  def annotations(holder: ScAnnotationsHolder): Seq[String] = {
    val convertibleAnnotations = holder.annotations.filterNot { a =>
      a.getQualifiedName match {
        case null => true
        case s if keywordAnnotations.keySet.contains(s) => true
        case s if Set("scala.throws", "scala.inline", "scala.unchecked").contains(s) => true
        case s if s.endsWith("BeanProperty") => true
        case _ => false
      }
    }
    convertibleAnnotations.map { a =>
      val fqn = a.getQualifiedName
      val args = convertArgs(a.constructor.args.toSeq.flatMap(_.exprs))
      s"@$fqn$args"
    }
  }

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
          call.args.exprs.map(convertExpression).mkString("{", ", ", "}")
        } else problem
      case call: ScGenericCall =>
        if (call.referencedExpr.getText.endsWith("classOf")) {
          val arguments = call.arguments
          if (arguments.length == 1) {
            val typeResult = arguments.head.getType(TypingContext.empty)
            typeResult match {
              case Success(tp, _) =>
                ScType.extractClass(tp, Some(e.getProject)) match {
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
                          case Some(constrArgs) => res += convertArgs(constrArgs.exprs)
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

  def convertArgs(args: Seq[ScExpression]): String = {
    if (args.isEmpty) ""
    else args.map(convertExpression).mkString("(", ", ", ")")
  }
}
