package org.jetbrains.plugins.scala.util

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

import scala.reflect.ClassTag

trait PsiSelectionUtil {
  type NamedElementPath = List[String]

  def selectElement[R <: PsiElement : ClassTag](elem: PsiElement, path: NamedElementPath, searchElement: Boolean = false): R = {
    val typeName = implicitly[ClassTag[R]].runtimeClass.getName
    def getInner(elem: PsiElement, path: List[String]): Either[String, R] = {
      def pathString = "/" + path.mkString("/")
      path match {
        case name :: rest =>
          for {
            candidate <- elem.depthFirst().collect {
              case e: ScNamedElement if e.name == name => e
              case r: ScReference if r.refName == name => r
            }
            found <- getInner(candidate, rest)
          } {
            return Right(found)
          }
          Left(s"Couldn't find path ${path.mkString("/")}")
        case _ if searchElement =>
          val foundElements = elem.depthFirst().collect { case e: R => e }.to(LazyList)
          foundElements match {
            case LazyList(foundElement) => Right(foundElement)
            case LazyList() => Left(s"Found no element of type $typeName in $pathString")
            case elements => Left(s"Found ${elements.length} elements of type $typeName in $pathString:\n${elements.map(_.getText).mkString("\n")}")
          }
        case _ =>
          elem match {
            case e: R => Right(e)
            case e => Left(s"Found element at path $pathString, but it is of type ${e.getClass.getName}, not expected $typeName")
          }
      }
    }

    getInner(elem, path) match {
      case Right(e) => e
      case Left(str) => throw new NoSuchElementException(str)
    }
  }

  def searchElement[R <: PsiElement : ClassTag](elem: PsiElement, path: NamedElementPath = List.empty): R =
    selectElement[R](elem, path, searchElement = true)

  def path(path: String*): List[String] = path.toList
}

object PsiSelectionUtil extends PsiSelectionUtil