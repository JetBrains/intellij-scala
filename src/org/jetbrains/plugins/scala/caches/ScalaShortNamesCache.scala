package org.jetbrains.plugins.scala.caches

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.util.containers.HashSet
import collection.mutable.ArrayBuffer
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTrait, ScClass, ScObject}
import stubs.StubIndex
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys
import com.intellij.util.{Processor, ArrayUtil}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

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
    for (clazz <- classes) {
      clazz match {
        case o: ScObject if isOkForJava(o) =>
          o.fakeCompanionClass match {
            case Some(clazz) => res += clazz
            case _ =>
          }
        case _ =>
      }
    }
    if (name.endsWith("$")) {
      val nameWithoutDollar = name.substring(0, name.length() - 1)
      val classes = ScalaShortNamesCacheManager.getInstance(project).getClassesByName(nameWithoutDollar, scope)
      for (clazz <- classes) {
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
      for (clazz <- classes) {
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

  private var LOG: Logger = Logger.getInstance("#org.jetbrains.plugins.scala.caches.ScalaShortNamesCache")
}