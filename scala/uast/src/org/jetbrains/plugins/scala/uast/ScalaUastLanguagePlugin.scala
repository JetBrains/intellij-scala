package org.jetbrains.plugins.scala
package uast

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher
import com.intellij.psi.{PsiClassInitializer, PsiElement, PsiMethod, PsiVariable}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.uast.converter.Scala2UastConverter._
import org.jetbrains.plugins.scala.lang.psi.uast.utils.NotNothing
import org.jetbrains.uast._

import scala.language.postfixOps

/**
 * [[UastLanguagePlugin]] implementation for the Scala plugin.
 */
final class ScalaUastLanguagePlugin extends UastLanguagePlugin {

  import UastLanguagePlugin._

  override def getLanguage: Language = ScalaLanguage.INSTANCE

  override def getPriority: Int = 10

  private val fileNameMatcher =
    new ExtensionFileNameMatcher(
      getLanguage.getAssociatedFileType.getDefaultExtension
    )

  override def isFileSupported(s: String): Boolean =
    fileNameMatcher.acceptsCharSequence(s)

  @Nullable
  override def convertElement(element: PsiElement,
                              @Nullable parent: UElement,
                              @Nullable requiredType: Class[_ <: UElement]): UElement =
    convertTo(element, parent)(
      toClassTag(requiredType),
      implicitly[NotNothing[UElement]]
    ).orNull

  @Nullable
  override def convertElementWithParent(element: PsiElement,
                                        @Nullable requiredType: Class[_ <: UElement]): UElement =
    convertWithParentTo(element)(
      toClassTag(requiredType),
      implicitly[NotNothing[UElement]]
    ).orNull

  // TODO:
  //  - add lazy vals where possible

  @Nullable
  override def getConstructorCallExpression(element: PsiElement,
                                            fqName: String): ResolvedConstructor =
    element match {
      case constructorCall: ScConstructorInvocation
        if constructorCall.typeElement
          .`type`()
          .exists(_.canonicalText == fqName) =>
        val resolvedConstructor =
          for {
            callExpression <- convertWithParentTo[UCallExpression](element)
            constructorMethod <- constructorCall.reference.map(
              _.resolveTo[PsiMethod]
            )
            containingClass <- Option(constructorMethod.getContainingClass)
            if containingClass.getQualifiedName == fqName
          } yield
            new ResolvedConstructor(
              callExpression,
              constructorMethod,
              containingClass
            )

        resolvedConstructor.orNull

      case _ => null
    }

  override def getInitializerBody(psiClassInitializer: PsiClassInitializer): UExpression =
    DefaultImpls.getInitializerBody(this, psiClassInitializer)

  @Nullable
  override def getInitializerBody(psiVariable: PsiVariable): UExpression =
    DefaultImpls.getInitializerBody(this, psiVariable)

  @Nullable
  override def getMethodBody(psiMethod: PsiMethod): UExpression =
    DefaultImpls.getMethodBody(this, psiMethod)

  @Nullable
  override def getMethodCallExpression(element: PsiElement,
                                       containingClassFqName: String,
                                       methodName: String): ResolvedMethod =
    element match {
      case methodCall: ScMethodCall
        if Option(methodCall.getInvokedExpr).exists {
          case ref: ScReference => ref.refName == methodName
          case _ => false
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
          .map(new ResolvedMethod(callExpression, _))
          .orNull
      case _ => null
    }

  override def isExpressionValueUsed(uExpression: UExpression): Boolean =
    throw new NotImplementedError // TODO: not implemented

  private def toClassTag(@Nullable requiredType: Class[_ <: UElement]) =
    reflect.ClassTag[UElement](if (requiredType == null) classOf[UElement] else requiredType)
}
