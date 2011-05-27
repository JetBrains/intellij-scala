package org.jetbrains.plugins.scala.caches

import com.intellij.openapi.util.Key
import collection.immutable.Map
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import com.intellij.psi.{PsiNamedElement, PsiElement}

/**
 * @author Alexander Podkhalyuzin
 */
object ScalaRecursionManager { //todo: is it really important to use mod count?
  val IMPLICIT_PARAM_TYPES_KEY: RecursionManagerKey[ScType] = Key.create("implicit.param.types.key")
  val CYCLIC_HELPER_KEY: RecursionManagerKey[PsiNamedElement] = Key.create("cyclic.helper.key")

  type RecursionManagerKey[T] = Key[Map[Thread, (Long, List[T])]]

  private def getSearches[Dom <: PsiElement, T](element: Dom, key: RecursionManagerKey[T]): List[T] = {
    val currentThread = Thread.currentThread
    import collection.immutable.Map
    val userData: Map[Thread, (Long, List[T])] = element.getUserData(key)
    val currentModificationCount = element.getManager.getModificationTracker.getModificationCount
    if (userData == null) {
      val emptySearches: List[T] = List.empty
      element.putUserData(key,
        Map(currentThread -> (currentModificationCount, emptySearches))
      )
      return emptySearches
    } else if (userData.get(currentThread) == None) {
      val emptySearches: List[T] = List.empty
      element.putUserData(key,
        userData + (currentThread -> (currentModificationCount, emptySearches))
      )
      return emptySearches
    }
    val (modCount, searches) = userData.get(currentThread).get
    if (modCount != currentModificationCount) {
      val emptySearches: List[T] = List.empty
      element.putUserData(key,
        userData - currentThread + (currentThread -> (currentModificationCount, emptySearches))
      )
      return emptySearches
    }
    searches
  }

  private def replaceSearches[Dom <: PsiElement, T](searches: List[T], element: Dom, key: RecursionManagerKey[T]) {
    val currentThread = Thread.currentThread
    import collection.immutable.Map
    val userData: Map[Thread, (Long, List[T])] = element.getUserData(key)
    //userData can't be null
    //searches also can't be null
    //modification count can't be different
    val currentModificationCount = element.getManager.getModificationTracker.getModificationCount
    element.putUserData(key,
      userData - currentThread + (currentThread -> (currentModificationCount, searches))
    )
  }

  /**
   * Do compatations stopping infinite recursion.
   * @param element store information about recursion stack
   * @param checkAdd checks if element is recursive to elements from stack
   * @param addElement element, which to add to recursion stack
   * @param compute computations body
   * @param key to store information about recursion stack
   */
  def doComputations[Dom <: PsiElement, T, Result](element: Dom, checkAdd: (T, List[T]) => Boolean, addElement: T,
                                                   compute: => Result, key: RecursionManagerKey[T]): Option[Result] = {
    val searches: List[T] = getSearches(element, key)
    if (checkAdd(addElement, searches)) {
      try {
        replaceSearches(addElement :: searches, element, key)

        //computations
        Some(compute)
      }
      finally {
        replaceSearches(searches, element, key)
      }
    }
    else None
  }

  def doComputationsForTwoElements[Dom <: PsiElement, T, Result](element1: Dom, element2: Dom,
                                                                 checkAdd: (T, List[T]) => Boolean,
                                                                 addElement1: T, addElement2: T, compute: => Result,
                                                                 key: RecursionManagerKey[T]): Option[Result] = {
    val searches1: List[T] = getSearches(element1, key)
    val searches2: List[T] = getSearches(element2, key)
    if (checkAdd(addElement1, searches1) && checkAdd(addElement2, searches2)) {
      try {
        replaceSearches(addElement1 :: searches1, element1, key)
        replaceSearches(addElement2 :: searches2, element2, key)

        //computations
        Some(compute)
      }
      finally {
        replaceSearches(searches1, element1, key)
        replaceSearches(searches2, element2, key)
      }
    }
    else None
  }

}