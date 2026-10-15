package org.jetbrains.plugins.scala.lang.psi

import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiClass
import org.jetbrains.annotations.ApiStatus

/**
 * Finds the inheritors of a class.
 *
 * Inheritor search runs on search executors and indexes, which are backend APIs, so the language layer only knows
 * this interface. Without an implementation (e.g. on a frontend) nothing is found.
 */
@ApiStatus.Internal
trait ScalaInheritorsFinder {

  /** The inheritors of a class in its own file, direct and indirect (e.g. the cases of a sealed class) */
  def inheritorsInFile(clazz: PsiClass): Iterable[PsiClass]

  /** The class and its inheritors, direct and indirect, in its resolve scope; only inheritors of Scala classes are searched */
  def allInheritors(clazz: PsiClass): Set[PsiClass]
}

object ScalaInheritorsFinder {

  def instance: ScalaInheritorsFinder =
    ApplicationManager.getApplication.getService(classOf[ScalaInheritorsFinder]) match {
      case null    => NoInheritorsFinder
      case service => service
    }

  private object NoInheritorsFinder extends ScalaInheritorsFinder {
    override def inheritorsInFile(clazz: PsiClass): Iterable[PsiClass] = Iterable.empty

    override def allInheritors(clazz: PsiClass): Set[PsiClass] = Option(clazz).toSet
  }
}
