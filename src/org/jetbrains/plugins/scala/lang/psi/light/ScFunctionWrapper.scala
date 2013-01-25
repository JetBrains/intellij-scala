package org.jetbrains.plugins.scala.lang.psi.light

import com.intellij.psi.impl.light.LightMethod
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import com.intellij.psi.{PsiClass, PsiElement, PsiMethod, JavaPsiFacade}
import collection.mutable.ArrayBuffer
import org.jetbrains.plugins.scala.lang.psi.types.{ScSubstitutor, StdType, ScType, ScCompoundType}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTypeDefinition, ScObject}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScMethodLike, ScPrimaryConstructor}

class ScPrimaryConstructorWrapper(val constr: ScPrimaryConstructor) extends {
  val elementFactory = JavaPsiFacade.getInstance(constr.getProject).getElementFactory
  val containingClass = {
      val res: PsiClass = constr.containingClass
      assert(res != null, s"Method: ${constr.getText}\nhas null containing class. \nContaining file text: ${constr.getContainingFile.getText}")
      res
  }
  val methodText = ScFunctionWrapper.methodText(constr, false, false, None)
  val method: PsiMethod = {
    try {
      elementFactory.createMethodFromText(methodText, containingClass)
    } catch {
      case e: Exception => elementFactory.createMethodFromText("public void FAILED_TO_DECOMPILE_METHOD() {}", containingClass)
    }
  }
} with LightMethod(constr.getManager, method, containingClass) with LightScalaMethod {
  override def getNavigationElement: PsiElement = constr.getNavigationElement

  override def canNavigate: Boolean = constr.canNavigate

  override def canNavigateToSource: Boolean = constr.canNavigateToSource

  override def getParent: PsiElement = containingClass

  override def getTextOffset: Int = constr.getTextOffset

  override def hasModifierProperty(name: String): Boolean = {
    name match {
      case _ => super.hasModifierProperty(name)
    }
  }

  override def getPrevSibling: PsiElement = constr.getPrevSibling

  override def getNextSibling: PsiElement = constr.getNextSibling
}

object ScPrimaryConstructorWrapper {

}

/**
 * @author Alefas
 * @since 27.02.12
 */
class ScFunctionWrapper(val function: ScFunction, isStatic: Boolean, isInterface: Boolean,
                        cClass: Option[PsiClass]) extends {
  val elementFactory = JavaPsiFacade.getInstance(function.getProject).getElementFactory
  val containingClass = {
    if (cClass != None) cClass.get
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
  val methodText = ScFunctionWrapper.methodText(function, isStatic, isInterface, cClass)
  val method: PsiMethod = {
    try {
      elementFactory.createMethodFromText(methodText, containingClass)
    } catch {
      case e: Exception => elementFactory.createMethodFromText("public void FAILED_TO_DECOMPILE_METHOD() {}", containingClass)
    }
  }
} with LightMethod(function.getManager, method, containingClass) with LightScalaMethod {
  override def getNavigationElement: PsiElement = function.getNavigationElement

  override def canNavigate: Boolean = function.canNavigate

  override def canNavigateToSource: Boolean = function.canNavigateToSource

  override def getParent: PsiElement = containingClass

  override def getTextOffset: Int = function.getTextOffset

  override def hasModifierProperty(name: String): Boolean = {
    name match {
      case "abstract" if isInterface => true
      case _ => super.hasModifierProperty(name)
    }
  }

  override def getPrevSibling: PsiElement = function.getPrevSibling

  override def getNextSibling: PsiElement = function.getNextSibling
}

object ScFunctionWrapper {
  /**
   * This is for Java only.
   */
  def methodText(function: ScMethodLike, isStatic: Boolean, isInterface: Boolean, cClass: Option[PsiClass]): String = {
    val builder = new StringBuilder

    builder.append(JavaConversionUtil.modifiers(function, isStatic))

    val subst = (cClass, function) match {
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

    function match {
      case function: ScFunction if function.typeParameters.length > 0 =>
        builder.append(function.typeParameters.map(tp => {
          var res = tp.name
          tp.upperTypeElement match {
            case Some(tParam) =>
              val classes = new ArrayBuffer[PsiClass]()
              tp.upperBound.map(subst.subst(_)) match {
                case Success(tp: ScCompoundType, _) =>
                  tp.components.foreach {
                    case tp: ScType => ScType.extractClass(tp, Some(function.getProject)) match {
                      case Some(clazz) => classes += clazz
                      case _ =>
                    }
                  }
                case Success(_: StdType, _) =>
                  JavaPsiFacade.getInstance(function.getProject).getElementFactory.
                    createTypeByFQClassName("java.lang.Object", function.getResolveScope)
                case Success(tp, _) =>
                  ScType.extractClass(tp, Some(function.getProject)) match {
                    case Some(clazz) => classes += clazz
                    case _ =>
                  }
                case _ =>
              }
              if (classes.length > 0) {
                res += classes.map(_.getQualifiedName).mkString(" extends ", " & ", "")
              }
            case _ =>
          }
          res
        }).mkString("<", ", ", ">"))
      case _ =>
    }

    function match {
      case function: ScFunction if !function.isConstructor =>
        function.returnType match {
          case Success(tp, _) => builder.append(JavaConversionUtil.typeText(subst.subst(tp), function.getProject, function.getResolveScope))
          case _ => builder.append("java.lang.Object")
        }
      case _ =>
    }

    builder.append(" ")
    val name = if (!function.isConstructor) function.getName else function.containingClass.getName
    builder.append(name)

    builder.append(function.effectiveParameterClauses.flatMap(_.parameters).map { case param =>
      val builder = new StringBuilder
      param.getRealParameterType(TypingContext.empty) match {
        case Success(tp, _) => builder.append(JavaConversionUtil.typeText(subst.subst(tp), function.getProject, function.getResolveScope))
        case _ => builder.append("java.lang.Object")
      }
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
}
