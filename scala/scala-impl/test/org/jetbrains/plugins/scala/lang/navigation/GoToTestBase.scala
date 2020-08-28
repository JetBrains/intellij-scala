package org.jetbrains.plugins.scala
package lang
package navigation

import com.intellij.psi.{PsiClass, PsiElement, PsiNamedElement, PsiPackage}
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValue
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject

import scala.reflect.ClassTag

abstract class GoToTestBase extends base.ScalaLightCodeInsightFixtureTestAdapter {

  protected final def actualName(actual: Any) = actual match {
    case pack: PsiPackage              => pack.getQualifiedName
    case clazz: PsiClass               => clazz.qualifiedName
    case namedElement: PsiNamedElement => namedElement.name
    case _                             => actual.toString
  }

  protected final def is[T: ClassTag](any: Any) = any.isInstanceOf[T @unchecked]

  protected final def isPackageObject(any: Any) = any match {
    case scObject: ScObject => scObject.isPackageObject
    case _ => false
  }

  protected final def isVal(any: Any) = any match {
    case named: ScNamedElement => named.nameContext.isInstanceOf[ScValue]
    case _ => false
  }

  protected final def isFromScalaSource(element: PsiElement) = element.getContainingFile match {
    case sf: ScFile => !sf.isCompiled
    case _ => false
  }
}
