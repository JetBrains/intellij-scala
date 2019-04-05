package org.jetbrains.plugins.scala.util

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference

import scala.reflect.ClassTag

trait PsiSelectionUtil {
  type NamedElementPath = List[String]

  def selectElement[R: ClassTag](elem: PsiElement, path: NamedElementPath): R = {
    def getInner(elem: PsiElement, path: List[String]): Option[R] = {
      path match {
        case name :: rest =>
          for {
            candidate <- elem.depthFirst().collect {
              case e: ScNamedElement if e.name == name => e
              case r: ScReference if r.refName == name => r
            }
            found <- getInner(candidate, rest)
          } {
            return Some(found)
          }
          None
        case _ =>
          Some(elem).collect { case e: R => e }
      }
    }

    getInner(elem, path).getOrElse(throw new NoSuchElementException(s"Element ${path.mkString(".")} was not found"))
  }

  def path(path: String*): List[String] = path.toList
}

object PsiSelectionUtil extends PsiSelectionUtil