package org.jetbrains.plugins.scala
package lang
package psi
package uast

import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher
import com.intellij.psi.{PsiClassInitializer, PsiElement, PsiMethod, PsiVariable}
import org.jetbrains.uast._

final class ScalaUastPlugin extends UastLanguagePlugin {

  import UastLanguagePlugin._

  import reflect.{ClassTag, classTag}

  override val getLanguage: ScalaLanguage = ScalaLanguage.INSTANCE

  override val getPriority = 0

  private val fileNameMatcher = new ExtensionFileNameMatcher(getLanguage.getAssociatedFileType.getDefaultExtension)

  override def convertElement(element: PsiElement, parent: UElement,
                              requiredType: Class[_ <: UElement]): UElement = null

  override def convertElementWithParent(element: PsiElement,
                                        requiredType: Class[_ <: UElement]): UElement = null

  override def getConstructorCallExpression(element: PsiElement,
                                            fqName: String): ResolvedConstructor = null

  override def getInitializerBody(classInitializer: PsiClassInitializer): UExpression =
    as[UClassInitializer](classInitializer)
      .map(_.getUastBody)
      .orNull

  override def getInitializerBody(variable: PsiVariable): UExpression =
    as[UVariable](variable)
      .fold(new UastEmptyExpression(null): UExpression)(_.getUastInitializer)

  override def getMethodBody(method: PsiMethod): UExpression =
    as[UMethod](method)
      .map(_.getUastBody)
      .orNull

  override def getMethodCallExpression(element: PsiElement,
                                       containingClassFqName: String,
                                       methodName: String): ResolvedMethod = null

  override def isExpressionValueUsed(expression: UExpression): Boolean = false

  override def isFileSupported(fileName: String): Boolean =
    fileNameMatcher.acceptsCharSequence(fileName)

  private def as[U <: UElement : ClassTag](element: PsiElement): Option[U] =
    if (classTag.runtimeClass.isInstance(element))
      Some(element.asInstanceOf[U])
    else
      Option(convertElementWithParent(element, null))
}
