package org.jetbrains.plugins.scala
package caches

import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.search.{GlobalSearchScope, PsiShortNamesCache}
import com.intellij.psi.stubs.StubIndex
import com.intellij.util.containers.HashSet
import com.intellij.util.{ArrayUtil, Processor}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys

import scala.collection.mutable.ArrayBuffer

/**
 * @author ilyas
 */
class ScalaShortNamesCache(implicit project: Project) extends PsiShortNamesCache {
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

    val cacheManager = ScalaShortNamesCacheManager.getInstance(project)

    val classes = cacheManager.getClassesByName(name, scope)
    var res = new ArrayBuffer[PsiClass]()

    classes.foreach {
      case o: ScObject if isOkForJava(o) =>
        o.fakeCompanionClass.foreach(res.+=)
      case _ =>
    }
    if (name.endsWith("$")) {
      val nameWithoutDollar = name.substring(0, name.length() - 1)
      val classes = cacheManager.getClassesByName(nameWithoutDollar, scope)

      classes.foreach {
        case c: ScTypeDefinition if isOkForJava(c) =>
          c.fakeCompanionModule.foreach(res.+=)
        case _ =>
      }
    } else if (name.endsWith("$class")) {
      val nameWithoutDollar = name.substring(0, name.length() - 6)
      val classes = cacheManager.getClassesByName(nameWithoutDollar, scope)

      classes.foreach {
        case c: ScTrait if isOkForJava(c) =>
          res += c.fakeCompanionClass
          c.fakeCompanionModule.foreach(res.+=)
        case _ =>
      }
    }

    res.toArray
  }

  def processMethodsWithName(name: String, scope: GlobalSearchScope, processor: Processor[PsiMethod]): Boolean = {
    //todo:
    true
  }

  def getAllClassNames: Array[String] = {
    import ScalaIndexKeys._
    ALL_CLASS_NAMES.allKeys.toArray
  }

  override def getAllClassNames(dest: HashSet[String]): Unit = {
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

  override def getAllMethodNames(set: HashSet[String]) {
    //todo:
  }

  def getFieldsByName(name: String, scope: GlobalSearchScope): Array[PsiField] = {
    PsiField.EMPTY_ARRAY //todo:
  }

  def getAllFieldNames: Array[String] = {
    ArrayUtil.EMPTY_STRING_ARRAY //todo:
  }

  override def getAllFieldNames(set: HashSet[String]) {
    //todo:
  }

  def getFieldsByNameIfNotMoreThan(name: String, scope: GlobalSearchScope, maxCount: Int): Array[PsiField] = {
    Array.empty //todo:
  }

}
