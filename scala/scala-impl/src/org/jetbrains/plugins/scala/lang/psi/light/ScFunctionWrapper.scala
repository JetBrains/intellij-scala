package org.jetbrains.plugins.scala.lang.psi.light

import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScMethodLike, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{PsiTypeParameterExt, ScParameter, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.api.{StdType, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.result._

import _root_.scala.collection.mutable.ArrayBuffer

class ScPrimaryConstructorWrapper(val delegate: ScPrimaryConstructor, isJavaVarargs: Boolean = false, forDefault: Option[Int] = None) extends {
  val containingClass: PsiClass = {
    val res: PsiClass = delegate.containingClass
    assert(res != null, s"Method: ${delegate.getText}\nhas null containing class. \nContaining file text: ${delegate.getContainingFile.getText}")
    res
  }
  val method: PsiMethod = {
    val methodText = ScFunctionWrapper.methodText(delegate, isStatic = forDefault.isDefined, isInterface = false, None, isJavaVarargs, forDefault)
    LightUtil.createJavaMethod(methodText, containingClass, delegate.getProject)
  }

} with PsiMethodWrapper(delegate.getManager, method, containingClass)
  with NavigablePsiElementWrapper[ScPrimaryConstructor] {

  override protected def returnType: ScType = {
    forDefault match {
      case Some(i) =>
        val param = delegate.parameters(i - 1)
        param.`type`().getOrAny
      case _ => null
    }
  }

  override protected def parameterListText: String = {
    val substitutor = ScFunctionWrapper.getSubstitutor(None, delegate)
    ScFunctionWrapper.parameterListText(delegate, substitutor, forDefault, isJavaVarargs)
  }

  override def isWritable: Boolean = getContainingFile.isWritable
}

object ScPrimaryConstructorWrapper {

  def unapply(wrapper: ScPrimaryConstructorWrapper): Option[ScPrimaryConstructor] = Some(wrapper.delegate)
}

/**
  * Represnts Scala functions for Java. It can do it in many ways including
  * default parameters. For example (forDefault = Some(1)):
  * def foo(x: Int = 1) generates method foo$default$1.
  *
  * @author Alefas
  * @since 27.02.12
  */
class ScFunctionWrapper(val delegate: ScFunction, isStatic: Boolean, isInterface: Boolean,
                        cClass: Option[PsiClass], isJavaVarargs: Boolean = false,
                        forDefault: Option[Int] = None) extends {
  val containingClass: PsiClass = {
    if (cClass.isDefined) cClass.get
    else {
      var res: PsiClass = delegate.containingClass
      if (isStatic) {
        res match {
          case o: ScObject => res = o.fakeCompanionClassOrCompanionClass
          case _ =>
        }
      }
      assert(res != null, "Method: " + delegate.getText + "\nhas null containing class. isStatic: " + isStatic +
        "\nContaining file text: " + delegate.getContainingFile.getText)
      res
    }
  }
  val method: PsiMethod = {
    val methodText = ScFunctionWrapper.methodText(delegate, isStatic, isInterface, cClass, isJavaVarargs, forDefault)
    LightUtil.createJavaMethod(methodText, containingClass, delegate.getProject)
  }

} with PsiMethodWrapper(delegate.getManager, method, containingClass)
  with NavigablePsiElementWrapper[ScFunction] {

  override def hasModifierProperty(name: String): Boolean = {
    name match {
      case "abstract" if isInterface => true
      case "final" if containingClass.isInstanceOf[ScTrait] => false //fix for SCL-5824
      case _ => super.hasModifierProperty(name)
    }
  }

  override protected def parameterListText: String = {
    val substitutor: ScSubstitutor = ScFunctionWrapper.getSubstitutor(cClass, delegate)
    ScFunctionWrapper.parameterListText(delegate, substitutor, forDefault, isJavaVarargs)
  }

  override protected def returnType: ScType = {
    val isConstructor = delegate.isConstructor && forDefault.isEmpty
    if (isConstructor) null
    else {
      val typeParameters = delegate.typeParameters
      val generifySubst: ScSubstitutor =
        if (typeParameters.nonEmpty) {
          val methodTypeParameters = getTypeParameters
          if (typeParameters.length == methodTypeParameters.length) {
            val tvs =
              typeParameters.zip(methodTypeParameters).map {
                case (param: ScTypeParam, parameter: PsiTypeParameter) =>
                  (param.nameAndId, ScDesignatorType(parameter))
              }
            ScSubstitutor(tvs.toMap)
          } else ScSubstitutor.empty
        } else ScSubstitutor.empty

      val substitutor: ScSubstitutor = ScFunctionWrapper.getSubstitutor(cClass, delegate)
      val scalaType = forDefault match {
        case Some(i) =>
          val param = delegate.parameters(i - 1)
          val paramType = substitutor.subst(param.`type`().getOrAny)
          generifySubst.subst(paramType)
        case None =>
          val retType = substitutor.subst(delegate.returnType.getOrAny)
          generifySubst.subst(retType)
      }
      scalaType
    }
  }

  override def getNameIdentifier: PsiIdentifier = delegate.getNameIdentifier

  override def isWritable: Boolean = getContainingFile.isWritable

  override def setName(name: String): PsiElement = {
    if (forDefault.isEmpty && !delegate.isConstructor) delegate.setName(name)
    else this
  }
}

object ScFunctionWrapper {

  def unapply(wrapper: ScFunctionWrapper): Option[ScFunction] = Some(wrapper.delegate)

  /**
    * This is for Java only.
    */
  def methodText(function: ScMethodLike, isStatic: Boolean, isInterface: Boolean, cClass: Option[PsiClass],
                 isJavaVarargs: Boolean, forDefault: Option[Int] = None): String = {
    val builder = new StringBuilder

    builder.append(JavaConversionUtil.annotationsAndModifiers(function, isStatic))

    builder.append(javaTypeParameters(function, getSubstitutor(cClass, function)))

    val isConstructor = function.isConstructor && forDefault.isEmpty
    if (!isConstructor) builder.append("java.lang.Object")

    builder.append(" ")
    val name = forDefault match {
      case Some(i) if function.isConstructor => "$lessinit$greater$default$" + i
      case Some(i) => function.getName + "$default$" + i
      case _ if function.isConstructor => function.containingClass.getName
      case _ => function.getName
    }
    builder.append(name)

    builder.append("()")

    function match {
      case function: ScFunction =>
        builder.append(LightUtil.getThrowsSection(function))
      case _ =>
    }

    if (!isInterface) {
      builder.append(" {}")
    } else {
      builder.append(";")
    }

    builder.toString()
  }

  private def javaTypeParameters(function: ScMethodLike, subst: ScSubstitutor): String = {
    function match {
      case fun: ScFunction if fun.typeParameters.nonEmpty =>
        fun.typeParameters.map(tp => {
          tp.upperTypeElement match {
            case Some(_) =>
              val classes = new ArrayBuffer[String]()
              val project = fun.getProject
              tp.upperBound.map(subst.subst) match {
                case Right(tp: ScCompoundType) =>
                  tp.components.foreach { tp: ScType =>
                    tp.extractClass match {
                      case Some(clazz) => classes += clazz.getQualifiedName
                      case _ =>
                    }
                  }
                case Right(_: StdType) =>
                case Right(tpt: TypeParameterType) =>
                  classes += tpt.canonicalText
                case Right(scType) =>
                  scType.extractClass match {
                    case Some(clazz) => classes += clazz.getQualifiedName
                    case _ =>
                  }
                case _ =>
              }
              if (classes.nonEmpty) classes.mkString(s"${tp.name} extends ", " & ", "")
              else tp.name
            case _ =>
              tp.name
          }
        }).mkString("<", ", ", ">")
      case _ => ""
    }
  }

  def getSubstitutor(cClass: Option[PsiClass], function: ScMethodLike): ScSubstitutor = {
    (cClass, function) match {
      case (Some(clazz), function: ScFunction) =>
        clazz match {
          case td: ScTypeDefinition =>
            td.signaturesByName(function.name).find(_.method == function) match {
              case Some(sign) => sign.substitutor
              case _ => ScSubstitutor.empty
            }
          case _ => ScSubstitutor.empty
        }
      case _ => ScSubstitutor.empty
    }
  }

  private def paramText(subst: ScSubstitutor, param: ScParameter, isJavaVarargs: Boolean)
                       (implicit elementScope: ElementScope): String = {
    val paramAnnotations = JavaConversionUtil.annotations(param).mkString(" ")
    val varargs: Boolean = param.isRepeatedParameter && isJavaVarargs

    val paramType =
      if (varargs) param.`type`()
      else param.getRealParameterType

    val typeText = paramType.map(subst.subst) match {
      case Right(tp) if param.isCallByNameParameter =>
        val psiType = tp match {
          case std: StdType => tp.typeSystem.stdToPsiType(std, noPrimitives = true)
          case _ => tp.toPsiType
        }
        s"scala.Function0<${psiType.getCanonicalText}>"
      case Right(tp) =>
        JavaConversionUtil.typeText(tp)
      case _ => "java.lang.Object"
    }
    val vararg = if (varargs) "..." else ""
    val paramName = param.getName

    s"$paramAnnotations $typeText$vararg $paramName".trim
  }

  private[light] def parameterListText(function: ScMethodLike, subst: ScSubstitutor, forDefault: Option[Int], isJavaVarargs: Boolean): String = {
    implicit val elementScope = function.elementScope
    val params = function.effectiveParameterClauses.flatMap(_.effectiveParameters)

    val defaultParam = forDefault match {
      case Some(i) => Some(params(i - 1))
      case None => None
    }

    val parameterClauses = function.effectiveParameterClauses.takeWhile { clause =>
      defaultParam match {
        case Some(param) => !clause.effectiveParameters.contains(param)
        case None => true
      }
    }
    parameterClauses.flatMap(_.effectiveParameters).map { param =>
      paramText(subst, param, isJavaVarargs)
    }.mkString("(", ", ", ")")
  }

}
