package org.jetbrains.plugins.scala.runner

import com.intellij.codeInsight.runner.JavaMainMethodProvider
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.util.ScalaMainMethodUtil
import org.jetbrains.plugins.scala.util.ScalaMainMethodUtil._

private object MyScalaMainMethodUtil {

  def findMainMethodFromContext(element: PsiElement): Option[MainMethodInfo] = {
    val res1 = findContainingMainMethod(element)
    val res2 = res1.orElse(findScala2MainMethodInContainingTopLevelObject(element))
    val res3 = res2.orElse(findMainMethodInParentFileOrPackagings(element))
    val res4 = res3.orElse(findMainClassWithProvider(element))
    res4
  }

  /** @return main method info if the method definition itself contains the element in it's tree */
  def findContainingMainMethod(element: PsiElement): Option[MainMethodInfo] = {
    val isScala3 = element.isInScala3File
    element.withParentsInFile.collectFirst {
      case funDef: ScFunctionDefinition if isScala2MainMethod(funDef) =>
        MainMethodInfo.Scala2Style(funDef, funDef.containingClass.asInstanceOf[ScObject], funDef.getFirstChild)
      case funDef: ScFunctionDefinition if isScala3 && isScala3MainMethod(funDef) =>
        MainMethodInfo.Scala3Style(funDef)
    }
  }

  /** @return scala2 main method info if the element is located in a top level object with Scala2-style main method
   *         (or inside toplevel class whose companion object has Scala2-style main method)
   */
  def findScala2MainMethodInContainingTopLevelObject(element: PsiElement): Option[MainMethodInfo.Scala2Style] =
    for {
      containingObject <- findContainingTopLevelObject(element)
      main             <- ScalaMainMethodUtil.findScala2MainMethod(containingObject)
    } yield {
      // always set an ancestor of the location as sourceElement to get correct run configuration precedence #SCL-11091
      val sourceElem =
        if (PsiTreeUtil.isAncestor(containingObject, element, false)) containingObject.fakeCompanionClassOrCompanionClass
        else element.getContainingFile
      MainMethodInfo.Scala2Style(main, containingObject, sourceElem)
    }

  private def findContainingTopLevelObject(element: PsiElement): Option[ScObject] = {
    val topLevelTypeDef = element.withParentsInFile.collectFirst { case td: ScTypeDefinition if td.isTopLevel => td }
    topLevelTypeDef.flatMap {
      case o: ScObject => Some(o)
      case t: ScTypeDefinition =>
        // if we are inside companion class of an object with Scala2 main (object with main method)
        // we should also be able to create configuration from it
        ScalaPsiUtil.getCompanionModule(t).flatMap {
          case o: ScObject => Some(o)
          case _ => None
        }
    }
  }

  /**
   * @return main method in containing file or parent packagings
   * @note there can be several top-level elements in the file in case there are inner packagings, example: {{{
   * package a
   *
   * object O1 { def main(args: Array[String]): Unit = {} }
   *
   * package b {
   *   object O2 { def main(args: Array[String]): Unit = {} }
   *   package c {
   *     <CARET>
   *     object O3 { def main(args: Array[String]): Unit = {} }
   *   }
   * }
   * }}}
   */
  private def findMainMethodInParentFileOrPackagings(element: PsiElement): Option[MainMethodInfo] = {
    val fromTopLevels = element.withParentsInFile.flatMap(maybeTopLevelMainMethod)
    fromTopLevels.nextOption()
  }

  def maybeTopLevelMainMethod(psiElement: PsiElement): Option[MainMethodInfo] =
    psiElement match {
      case f: ScalaFile   => findMainMethodInMembers(f.members)
      case p: ScPackaging => findMainMethodInMembers(p.members)
      case _              => None
    }

  private def findMainMethodInMembers(members: Seq[ScMember]): Option[MainMethodInfo] = {
    val iterator = for {
      member <- members.iterator
      result <-  member match {
        case o: ScObject                                      => ScalaMainMethodUtil.findScala2MainMethod(o).map(MainMethodInfo.Scala2Style(_, o, o))
        case d: ScFunctionDefinition if isScala3MainMethod(d) => Some(MainMethodInfo.Scala3Style(d))
        case _                                                => None
      }
    } yield result
    iterator.nextOption()
  }

  /**
   * Mainly required for launching JavaFX without main method (see SCL-12132)
   *
   * @note implementation is very similar to [[com.intellij.psi.util.PsiMethodUtil.hasMainMethod]], we might unify the implementation
   *
   * @see org.jetbrains.plugins.javaFX.JavaFxMainMethodRunConfigurationProvider
   * @note From JavaFX docx:
   *       "The main() method is not required for JavaFX applications when the JAR file for the application is created with the JavaFX Packager tool..."
   */
  def hasMainMethodFromProviders(c: PsiClass): Boolean = {
    val extensions = JavaMainMethodProvider.EP_NAME.getExtensions
    val foundExtension = extensions.find { e =>
      e.isApplicable(c) && e.hasMainMethod(c)
    }
    foundExtension.nonEmpty
  }

  private def findMainClassWithProvider(element: PsiElement): Option[MainMethodInfo.WithCustomLauncher] = {
    val classes = element.withParentsInFile.filterByType[ScTypeDefinition]
    val clazz = classes.find(hasMainMethodFromProviders)
    clazz.map(MainMethodInfo.WithCustomLauncher.apply)
  }

}
