package org.jetbrains.plugins.scala.lang.psi.uast

import com.intellij.lang.Language
import com.intellij.psi.{PsiClassInitializer, PsiElement, PsiMethod, PsiVariable}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.uast.converter.Scala2UastConverter
import org.jetbrains.plugins.scala.lang.psi.uast.internals.ResolveCommon
import org.jetbrains.plugins.scala.lang.psi.uast.utils.NotNothing
import org.jetbrains.uast._

import scala.language.postfixOps
import scala.meta.trees.UnimplementedException
import scala.reflect.ClassTag

/**
  * [[UastLanguagePlugin]] implementation for the Scala plugin.
  */
class ScalaUastLanguagePlugin extends UastLanguagePlugin {

  override def getLanguage: Language = ScalaLanguage.INSTANCE

  override def getPriority: Int = 10

  override def isFileSupported(s: String): Boolean =
    s.endsWith(".scala") || s.endsWith(".sc")

  @Nullable
  override def convertElement(
    element: PsiElement,
    @Nullable parent: UElement,
    @Nullable requiredType: Class[_ <: UElement]
  ): UElement =
    Scala2UastConverter
      .convertTo(element, parent)(
        ClassTag[UElement](Option(requiredType).getOrElse(classOf[UElement])),
        implicitly[NotNothing[UElement]]
      )
      .orNull

  @Nullable
  override def convertElementWithParent(
    element: PsiElement,
    @Nullable requiredType: Class[_ <: UElement]
  ): UElement =
    Scala2UastConverter
      .convertWithParentTo(element)(
        ClassTag[UElement](Option(requiredType).getOrElse(classOf[UElement])),
        implicitly[NotNothing[UElement]]
      )
      .orNull

  // TODO:
  //  - add lazy vals where possible

  @Nullable
  override def getConstructorCallExpression(
    element: PsiElement,
    fqName: String
  ): UastLanguagePlugin.ResolvedConstructor =
    element match {
      case constructorCall: ScConstructorInvocation
          if constructorCall.typeElement
            .`type`()
            .exists(_.canonicalText == fqName) =>
        val resolvedConstructor =
          for {
            callExpression <- Option(convertElementWithParent(element, null))
              .collect { case c: UCallExpression => c }
            constructorMethod <- ResolveCommon.resolve[PsiMethod](
              constructorCall.reference
            )
            containingClass <- Option(constructorMethod.getContainingClass)
            if containingClass.getQualifiedName == fqName
          } yield
            new UastLanguagePlugin.ResolvedConstructor(
              callExpression,
              constructorMethod,
              containingClass
            )

        resolvedConstructor.orNull

      case _ => null
    }

  override def getInitializerBody(
    psiClassInitializer: PsiClassInitializer
  ): UExpression =
    UastLanguagePlugin.DefaultImpls
      .getInitializerBody(this, psiClassInitializer)

  @Nullable
  override def getInitializerBody(psiVariable: PsiVariable): UExpression =
    UastLanguagePlugin.DefaultImpls.getInitializerBody(this, psiVariable)

  @Nullable
  override def getMethodBody(psiMethod: PsiMethod): UExpression =
    UastLanguagePlugin.DefaultImpls.getMethodBody(this, psiMethod)

  @Nullable
  override def getMethodCallExpression(
    element: PsiElement,
    containingClassFqName: String,
    methodName: String
  ): UastLanguagePlugin.ResolvedMethod =
    element match {
      case methodCall: ScMethodCall
          if Option(methodCall.getInvokedExpr).exists {
            case ref: ScReference => ref.refName == methodName
            case _                => false
          } =>
        val callExpression = convertElementWithParent(methodCall, null) match {
          case callExpr: UCallExpression => callExpr
          case qualifiedRef: UQualifiedReferenceExpression
              if qualifiedRef.getSelector.isInstanceOf[UCallExpression] =>
            qualifiedRef.getSelector.asInstanceOf[UCallExpression]
          case otherwise => sys.error(s"Invalid element type: $otherwise")
        }

        Option(callExpression.resolve())
          .filter { method =>
            if (containingClassFqName != null) {
              val containingClass = method.getContainingClass
              containingClass != null && containingClass.getQualifiedName == containingClassFqName
            } else true
          }
          .map(new UastLanguagePlugin.ResolvedMethod(callExpression, _))
          .orNull
      case _ => null
    }

  override def isExpressionValueUsed(uExpression: UExpression): Boolean =
    throw new UnimplementedException("") // TODO: not implemented
}
