package org.jetbrains.plugins.scala
package lang.psi.light

import com.intellij.psi.{PsiClass, PsiClassType}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScAnnotationsHolder
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.types.{ScParameterizedType, ScType}

/**
 * @author Alefas
 * @since 07.12.12
 */
object LightUtil {
  /**
   * for Java only
   * @param holder annotation holder
   * @return Java throws section string or empty string
   */
  def getThrowsSection(holder: ScAnnotationsHolder): String = {
    holder.hasAnnotation("scala.throws") match {
      case Some(annotation) =>
        val classes = annotation.constructor.args.map(_.exprs).getOrElse(Seq.empty).flatMap { expr =>
          expr.getType(TypingContext.empty) match {
            case Success(ScParameterizedType(des, Seq(arg)), _) => ScType.extractClass(des) match {
              case Some(clazz) if clazz.qualifiedName == "java.lang.Class" =>
                ScType.toPsi(arg, holder.getProject, holder.getResolveScope) match {
                  case c: PsiClassType =>
                    c.resolve() match {
                      case clazz: PsiClass => Seq(clazz.getQualifiedName)
                      case _ => Seq.empty
                    }
                  case _ => Seq.empty
                }
              case _ => Seq.empty
            }
            case _ => Seq.empty
          }
        }
        if (classes.length == 0) {
          annotation.constructor.typeArgList match {
            case Some(args) =>
              val classes = args.typeArgs.map(_.getType(TypingContext.empty)).filter(_.isDefined).map(_.get).flatMap { arg =>
                ScType.toPsi(arg, holder.getProject, holder.getResolveScope) match {
                  case c: PsiClassType =>
                    c.resolve() match {
                      case clazz: PsiClass => Seq(clazz.getQualifiedName)
                      case _ => Seq.empty
                    }
                  case _ => Seq.empty
                }
              }
              if (!classes.isEmpty) classes.mkString(" throws ", ", ", " ")
              else ""
            case None => ""
          }
        } else classes.mkString(" throws ", ", ", " ")
      case _ => ""
    }
  }
}
