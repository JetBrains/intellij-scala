package org.jetbrains.plugins.scala.lang.navigation

import com.intellij.psi.{PsiClass, PsiElement, PsiNamedElement, PsiPackage}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiClassExt, PsiMemberExt, PsiNamedElementExt, invokeAndWait}
import org.jetbrains.plugins.scala.lang.psi.api.ScFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValue
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject}

import scala.reflect.ClassTag

abstract class GoToTestBase
  extends ScalaLightCodeInsightFixtureTestCase
    with GoToTestUtilities

trait GoToTestUtilities {

  protected final def actualName(actual: Any): String = invokeAndWait {
    //NOTE: using extra method to avoid issues with NonLocalReturnControl thrown from inside invokeAndWait
    //`invokeAndWait` wraps exceptions into extra RuntimeException and knows nothing about Scala NonLocalReturnControl.
    //Thus, Scala code can't catch the exception and throw it back to the test code.
    //
    //We could also handle it by handling RuntimeException in `extensions.preservingControlFlow` or asking the platform
    // to throw InvocationTargetException instead of RuntimeExceptions (to please preservingControlFlow)
    actualNameImpl(actual)
  }

  private def actualNameImpl(actual: Any): String = {
    actual match {
      case member: ScMember =>
        if (member.isTopLevel) {
          member.qualifiedNameOpt match {
            case Some(value) =>
              return value
            case _ =>
          }
        }
      case _ =>
    }

    actual match {
      case pack: PsiPackage => pack.getQualifiedName
      case clazz: PsiClass => clazz.qualifiedName
      case namedElement: PsiNamedElement => namedElement.name
      case _ => actual.toString
    }
  }

  protected final def is[T: ClassTag](any: Any): Boolean = any.is[T]

  protected final def isPackageObject(any: Any): Boolean = any match {
    case scObject: ScObject => scObject.isPackageObject
    case _ => false
  }

  protected final def isVal(any: Any): Boolean = any match {
    case named: ScNamedElement => named.nameContext.is[ScValue]
    case _ => false
  }

  protected final def isFromScalaSource(element: PsiElement): Boolean = element.getContainingFile match {
    case sf: ScFile => !sf.isCompiled
    case _ => false
  }
}
