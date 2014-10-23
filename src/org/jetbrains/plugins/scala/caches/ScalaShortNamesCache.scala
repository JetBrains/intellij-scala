package org.jetbrains.plugins.scala
package caches

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.search.{GlobalSearchScope, PsiShortNamesCache}
import com.intellij.psi.stubs.StubIndex
import com.intellij.util.containers.HashSet
import com.intellij.util.{ArrayUtil, Processor}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys

import scala.collection.mutable.ArrayBuffer

/**
 * @author ilyas
 */
class ScalaShortNamesCache(project: Project) extends PsiShortNamesCache {
  def getClassesByName(name: String, scope: GlobalSearchScope): Array[PsiClass] = {
    def isOkForJava(elem: ScalaPsiElement): Boolean = {
      var res = true
      var element = elem.getParent
      while (element != null && res) {
        element match {
          case o: ScObject if o.isPackageObject => res = false
          case _ =>
        }
        element = element.getParent
      }
      res
    }
    val classes = ScalaShortNamesCacheManager.getInstance(project).getClassesByName(name, scope)
    val res = new ArrayBuffer[PsiClass]
    val classesIterator = classes.iterator
    while (classesIterator.hasNext) {
      val clazz = classesIterator.next()
      clazz match {
        case o: ScObject if isOkForJava(o) =>
          o.fakeCompanionClass match {
            case Some(clz) => res += clz
            case _ =>
          }
        case _ =>
      }
    }
    if (name.endsWith("$")) {
      val nameWithoutDollar = name.substring(0, name.length() - 1)
      val classes = ScalaShortNamesCacheManager.getInstance(project).getClassesByName(nameWithoutDollar, scope)
      val classesIterator = classes.iterator
      while (classesIterator.hasNext) {
        val clazz = classesIterator.next()
        clazz match {
          case c: ScClass if isOkForJava(c) =>
            c.fakeCompanionModule match {
              case Some(o) => res += o
              case _ =>
            }
          case _ =>
        }
      }
    } else if (name.endsWith("$class")) {
      val nameWithoutDollar = name.substring(0, name.length() - 6)
      val classes = ScalaShortNamesCacheManager.getInstance(project).getClassesByName(nameWithoutDollar, scope)
      val classesIterator = classes.iterator
      while (classesIterator.hasNext) {
        val clazz = classesIterator.next()
        clazz match {
          case c: ScTrait if isOkForJava(c) =>
            res += c.fakeCompanionClass
          case _ =>
        }
      }
    }
    res.toArray
  }

  def processMethodsWithName(name: String, scope: GlobalSearchScope, processor: Processor[PsiMethod]): Boolean = {
    //todo:
    true
  }

  def getAllClassNames: Array[String] = {
    val keys = StubIndex.getInstance.getAllKeys(ScalaIndexKeys.ALL_CLASS_NAMES, project)
    keys.toArray(new Array[String](keys.size()))
  }

  def getAllClassNames(dest: HashSet[String]) {
    val keys = StubIndex.getInstance.getAllKeys(ScalaIndexKeys.ALL_CLASS_NAMES, project)
    dest.addAll(keys)
  }

  def getMethodsByName(name: String, scope: GlobalSearchScope): Array[PsiMethod] = {
    PsiMethod.EMPTY_ARRAY //todo:
  }

  def getMethodsByNameIfNotMoreThan(name: String, scope: GlobalSearchScope, maxCount: Int): Array[PsiMethod] = {
    getMethodsByName(name, scope) //todo:
  }

  def getAllMethodNames: Array[String] = {
    ArrayUtil.EMPTY_STRING_ARRAY //todo:
  }

  def getAllMethodNames(set: HashSet[String]) {
    //todo:
  }

  def getFieldsByName(name: String, scope: GlobalSearchScope): Array[PsiField] = {
    PsiField.EMPTY_ARRAY //todo:
  }

  def getAllFieldNames: Array[String] = {
    ArrayUtil.EMPTY_STRING_ARRAY //todo:
  }

  def getAllFieldNames(set: HashSet[String]) {
    //todo:
  }

  def getFieldsByNameIfNotMoreThan(name: String, scope: GlobalSearchScope, maxCount: Int): Array[PsiField] = {
    Array.empty //todo:
  }

  private var LOG: Logger = Logger.getInstance("#org.jetbrains.plugins.scala.caches.ScalaShortNamesCache")
}