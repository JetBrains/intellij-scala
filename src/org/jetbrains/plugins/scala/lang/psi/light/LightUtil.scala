package org.jetbrains.plugins.scala
package lang.psi.light

import com.intellij.openapi.project.Project
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScAnnotationsHolder
import org.jetbrains.plugins.scala.lang.psi.types.ScTypeExt
import org.jetbrains.plugins.scala.lang.psi.types.api.{ParameterizedType, TypeSystem}
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}

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
  def getThrowsSection(holder: ScAnnotationsHolder)
                      (implicit typeSystem: TypeSystem = holder.typeSystem): String = {
    val throwAnnotations = holder.annotations("scala.throws").foldLeft[ArrayBuffer[String]](ArrayBuffer()) {
      case (accumulator, annotation) =>
        implicit val elementScope = holder.elementScope

        val classes = annotation.constructor.args.map(_.exprs).getOrElse(Seq.empty).flatMap {
          _.getType(TypingContext.empty) match {
            case Success(ParameterizedType(des, Seq(arg)), _) => des.extractClass(elementScope.project) match {
              case Some(clazz) if clazz.qualifiedName == "java.lang.Class" =>
                arg.toPsiType() match {
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
                _.toPsiType() match {
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
    if (throwAnnotations.isEmpty) ""
    else throwAnnotations.mkString(start = " throws ", sep = ", ", end = " ")
  }

  def createJavaMethod(methodText: String, containingClass: PsiClass, project: Project): PsiMethod = {
    val elementFactory = JavaPsiFacade.getInstance(project).getElementFactory

    try elementFactory.createMethodFromText(methodText, containingClass)
    catch {
      case _: Exception => elementFactory.createMethodFromText("public void FAILED_TO_DECOMPILE_METHOD() {}", containingClass)
    }
  }

  def javaTypeElement(tp: PsiType, context: PsiElement, project: Project): PsiTypeElement = {
    val elementFactory = JavaPsiFacade.getInstance(project).getElementFactory
    elementFactory.createTypeElementFromText(tp.getCanonicalText, context)
  }
}
