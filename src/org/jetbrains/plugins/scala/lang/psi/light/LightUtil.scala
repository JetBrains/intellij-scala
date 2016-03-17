package org.jetbrains.plugins.scala
package lang.psi.light

import com.intellij.psi.{PsiClass, PsiClassType}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScAnnotationsHolder
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.types.{ScParameterizedType, ScType, ScTypeExt}

import _root_.scala.collection.mutable.ArrayBuffer

/**
 * @author Alefas
 * @since 07.12.12
 */
object LightUtil {
  /**
   * for Java only
    *
    * @param holder annotation holder
   * @return Java throws section string or empty string
   */
  def getThrowsSection(holder: ScAnnotationsHolder): String = {
    val throwAnnotations = holder.allMatchingAnnotations("scala.throws").foldLeft[ArrayBuffer[String]](ArrayBuffer()) {
      case (accumulator, annotation) =>
        val classes = annotation.constructor.args.map(_.exprs).getOrElse(Seq.empty).flatMap {
          _.getType(TypingContext.empty) match {
            case Success(ScParameterizedType(des, Seq(arg)), _) => ScType.extractClass(des) match {
              case Some(clazz) if clazz.qualifiedName == "java.lang.Class" =>
                arg.toPsiType(holder.getProject, holder.getResolveScope) match {
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
        if (classes.isEmpty) {
          annotation.constructor.typeArgList match {
            case Some(args) =>
              val classes = args.typeArgs.map(_.getType(TypingContext.empty)).filter(_.isDefined).map(_.get).flatMap {
                _.toPsiType(holder.getProject, holder.getResolveScope) match {
                  case c: PsiClassType =>
                    c.resolve() match {
                      case clazz: PsiClass => Seq(clazz.getQualifiedName)
                      case _ => Seq.empty
                    }
                  case _ => Seq.empty
                }
              }
              if (classes.nonEmpty) accumulator :+ classes.mkString(sep = ", ")
              else accumulator
            case None => accumulator
          }
        } else accumulator :+ classes.mkString(sep = ", ")
      case _ => ArrayBuffer()
    }
    throwAnnotations.mkString(start = " throws ", sep = ", ", end = " ")
  }
}
