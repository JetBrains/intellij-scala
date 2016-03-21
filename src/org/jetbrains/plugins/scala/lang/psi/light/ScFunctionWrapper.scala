package org.jetbrains.plugins.scala.lang.psi.light

import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScMethodLike, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypeResult, TypingContext}
import org.jetbrains.plugins.scala.project.ProjectExt

import _root_.scala.collection.mutable.ArrayBuffer

class ScPrimaryConstructorWrapper(val constr: ScPrimaryConstructor, isJavaVarargs: Boolean = false, forDefault: Option[Int] = None) extends {
  val elementFactory = JavaPsiFacade.getInstance(constr.getProject).getElementFactory
  val containingClass = {
      val res: PsiClass = constr.containingClass
      assert(res != null, s"Method: ${constr.getText}\nhas null containing class. \nContaining file text: ${constr.getContainingFile.getText}")
      res
  }
  val methodText = ScFunctionWrapper.methodText(constr, isStatic = forDefault.isDefined, isInterface = false, None, isJavaVarargs, forDefault)
  val method: PsiMethod = {
    try {
      elementFactory.createMethodFromText(methodText, containingClass)
    } catch {
      case e: Exception => elementFactory.createMethodFromText("public void FAILED_TO_DECOMPILE_METHOD() {}", containingClass)
    }
  }
} with LightMethodAdapter(constr.getManager, method, containingClass) with LightScalaMethod {

  override def getNavigationElement: PsiElement = this

  override def canNavigate: Boolean = constr.canNavigate

  override def canNavigateToSource: Boolean = constr.canNavigateToSource

  override def navigate(requestFocus: Boolean): Unit = constr.navigate(requestFocus)

  override def getTextRange: TextRange = constr.getTextRange

  override def getParent: PsiElement = containingClass

  override def getTextOffset: Int = constr.getTextOffset

  var returnType: PsiType = null

  override def getReturnType: PsiType = {
    forDefault match {
      case Some(i) if returnType == null =>
        val param = constr.parameters(i - 1)
        val scalaType = param.getType(TypingContext.empty).getOrAny
        returnType = scalaType.toPsiType(constr.getProject, constr.getResolveScope)
      case _ =>
    }
    returnType
  }

  override def getPrevSibling: PsiElement = constr.getPrevSibling

  override def getNextSibling: PsiElement = constr.getNextSibling

  override def isWritable: Boolean = getContainingFile.isWritable
}

object ScPrimaryConstructorWrapper {

}

/**
 * Represnts Scala functions for Java. It can do it in many ways including
 * default parameters. For example (forDefault = Some(1)):
 * def foo(x: Int = 1) generates method foo$default$1.
  *
  * @author Alefas
 * @since 27.02.12
 */
class ScFunctionWrapper(val function: ScFunction, isStatic: Boolean, isInterface: Boolean,
                        cClass: Option[PsiClass], isJavaVarargs: Boolean = false,
                        forDefault: Option[Int] = None) extends {
  val elementFactory = JavaPsiFacade.getInstance(function.getProject).getElementFactory
  val containingClass = {
    if (cClass.isDefined) cClass.get
    else {
      var res: PsiClass = function.containingClass
      if (isStatic) {
        res match {
          case o: ScObject => res = o.fakeCompanionClassOrCompanionClass
          case _ =>
        }
      }
      assert(res != null, "Method: " + function.getText + "\nhas null containing class. isStatic: " + isStatic +
        "\nContaining file text: " + function.getContainingFile.getText)
      res
    }
  }
  val methodText = ScFunctionWrapper.methodText(function, isStatic, isInterface, cClass, isJavaVarargs, forDefault)
  val method: PsiMethod = {
    try {
      elementFactory.createMethodFromText(methodText, containingClass)
    } catch {
      case e: Exception => elementFactory.createMethodFromText("public void FAILED_TO_DECOMPILE_METHOD() {}", containingClass)
    }
  }
} with LightMethodAdapter(function.getManager, method, containingClass) with LightScalaMethod {

  override def getNavigationElement: PsiElement = this

  override def canNavigate: Boolean = function.canNavigate

  override def canNavigateToSource: Boolean = function.canNavigateToSource

  override def navigate(requestFocus: Boolean): Unit = function.navigate(requestFocus)

  override def getParent: PsiElement = containingClass

  override def getTextOffset: Int = function.getTextOffset

  override def getTextRange: TextRange = function.getTextRange

  override def hasModifierProperty(name: String): Boolean = {
    name match {
      case "abstract" if isInterface => true
      case "final" if containingClass.isInstanceOf[ScTrait] => false //fix for SCL-5824
      case _ => super.hasModifierProperty(name)
    }
  }

  override def getPrevSibling: PsiElement = function.getPrevSibling

  override def getNextSibling: PsiElement = function.getNextSibling

  @volatile
  private var returnType: PsiType = null

  override def getReturnType: PsiType = {
    val isConstructor = function.isConstructor && forDefault.isEmpty
    if (returnType == null && !isConstructor) {
      val typeParameters = function.typeParameters
      val generifySubst: ScSubstitutor =
        if (typeParameters.nonEmpty) {
          val methodTypeParameters = getTypeParameters
          if (typeParameters.length == methodTypeParameters.length) {
            val tvs =
              typeParameters.zip(methodTypeParameters).map {
                case (param: ScTypeParam, parameter: PsiTypeParameter) =>
                  ((param.name, ScalaPsiUtil.getPsiElementId(param)), ScDesignatorType(parameter))
              }
            new ScSubstitutor(tvs.toMap, Map.empty, None)
          } else ScSubstitutor.empty
        } else ScSubstitutor.empty
      def lift: ScType => PsiType = _.toPsiType(function.getProject, function.getResolveScope)
      forDefault match {
        case Some(i) =>
          val param = function.parameters(i - 1)
          val scalaType = generifySubst subst ScFunctionWrapper.getSubstitutor(cClass, function).
            subst(param.getType(TypingContext.empty).getOrAny)
          returnType = lift(scalaType)
        case None =>
          val scalaType = generifySubst subst ScFunctionWrapper.getSubstitutor(cClass, function).
            subst(function.returnType.getOrAny)
          returnType = lift(scalaType)
      }
    }
    returnType
  }

  override def getNameIdentifier = function.getNameIdentifier

  override def isWritable: Boolean = getContainingFile.isWritable

  override def setName(name: String) = {
    if (forDefault.isEmpty && !function.isConstructor) function.setName(name)
    else this
  }
}

object ScFunctionWrapper {
  /**
   * This is for Java only.
   */
  def methodText(function: ScMethodLike, isStatic: Boolean, isInterface: Boolean, cClass: Option[PsiClass],
                 isJavaVarargs: Boolean, forDefault: Option[Int] = None): String = {
    val builder = new StringBuilder

    builder.append(JavaConversionUtil.annotationsAndModifiers(function, isStatic))

    val subst = getSubstitutor(cClass, function)

    function match {
      case function: ScFunction if function.typeParameters.nonEmpty =>
        builder.append(function.typeParameters.map(tp => {
          var res = tp.name
          tp.upperTypeElement match {
            case Some(tParam) =>
              val classes = new ArrayBuffer[String]()
              val project = function.getProject
              implicit val typeSystem = project.typeSystem
              tp.upperBound.map(subst.subst) match {
                case Success(tp: ScCompoundType, _) =>
                  tp.components.foreach {
                    case tp: ScType => tp.extractClass(project) match {
                      case Some(clazz) => classes += clazz.getQualifiedName
                      case _ =>
                    }
                  }
                case Success(_: StdType, _) =>
                  JavaPsiFacade.getInstance(project).getElementFactory.
                    createTypeByFQClassName("java.lang.Object", function.getResolveScope)
                case Success(tpt: ScTypeParameterType, _) =>
                  classes += tpt.canonicalText
                case Success(scType, _) =>
                  scType.extractClass(project) match {
                    case Some(clazz) => classes += clazz.getQualifiedName
                    case _ =>
                  }
                case _ =>
              }
              if (classes.nonEmpty) {
                res += classes.mkString(" extends ", " & ", "")
              }
            case _ =>
          }
          res
        }).mkString("<", ", ", ">"))
      case _ =>
    }

    val params = function.effectiveParameterClauses.flatMap(_.effectiveParameters)

    val defaultParam = forDefault match {
      case Some(i) => Some(params(i - 1))
      case None => None
    }

    def evalType(typeResult: TypeResult[ScType]) {
      typeResult match {
        case Success(tp, _) =>
          val typeText = JavaConversionUtil.typeText(subst.subst(tp), function.getProject, function.getResolveScope)
          builder.append(typeText)
        case _ => builder.append("java.lang.Object")
      }
    }
    defaultParam match {
      case Some(param) => evalType(param.getType(TypingContext.empty))
      case _ => function match {
        case fun: ScFunction if !fun.isConstructor =>
          if (fun.hasExplicitType) evalType(fun.returnType)
          else builder.append("FromTypeInference")
        case _ =>
      }
    }

    builder.append(" ")
    val name = forDefault match {
      case Some(i) if function.isConstructor => "$lessinit$greater$default$" + i
      case Some(i) => function.getName + "$default$" + i
      case _ if function.isConstructor => function.containingClass.getName
      case _ => function.getName
    }
    builder.append(name)

    builder.append(function.effectiveParameterClauses.takeWhile { clause =>
      defaultParam match {
        case Some(param) => !clause.effectiveParameters.contains(param)
        case None => true
      }
    }.flatMap(_.effectiveParameters).map { case param =>
      val builder = new StringBuilder
      val varargs: Boolean = param.isRepeatedParameter && isJavaVarargs
      val paramAnnotations = JavaConversionUtil.annotations(param).mkString(" ")
      if (!paramAnnotations.isEmpty)
        builder.append(paramAnnotations).append(" ")
      val tt =
        if (varargs) param.getType(TypingContext.empty)
        else param.getRealParameterType(TypingContext.empty)
      tt match {
        case Success(tp, _) if param.isCallByNameParameter =>
          builder.append("scala.Function0<")
          val psiType = subst.subst(tp).toPsiType(function.getProject, function.getResolveScope, noPrimitives = true)
          builder.append(psiType.getCanonicalText)
          builder.append(">")
        case Success(tp, _) =>
          builder.append(JavaConversionUtil.typeText(subst.subst(tp), function.getProject, function.getResolveScope))
        case _ => builder.append("java.lang.Object")
      }

      if (varargs) builder.append("...")

      builder.append(" ").append(param.getName)
      builder.toString()
    }.mkString("(", ", ", ")"))

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

  def getSubstitutor(cClass: Option[PsiClass], function: ScMethodLike): ScSubstitutor = {
    (cClass, function) match {
      case (Some(clazz), function: ScFunction) =>
        clazz match {
          case td: ScTypeDefinition =>
            td.signaturesByName(function.name).find(_.method == function) match {
              case Some(sign) => sign.substitutor
              case _          => ScSubstitutor.empty
            }
          case _ => ScSubstitutor.empty
        }
      case _ => ScSubstitutor.empty
    }
  }
}
