package org.jetbrains.plugins.scala.lang.psi.implicits

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameterType
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import scala.collection.{Seq, Set}

/**
  * @author Nikolay.Tropin
  */
class ImplicitCollectorCache(project: Project) {
  private val map =
    ContainerUtil.newConcurrentMap[(ImplicitSearchScope, ScType), Seq[ScalaResolveResult]]()

  private val typeParametersOwnersCache =
    ContainerUtil.newConcurrentMap[ScType, Set[ScTypeParametersOwner]]


  def get(place: PsiElement, tp: ScType): Option[Seq[ScalaResolveResult]] = {
    val scope = ImplicitSearchScope.forElement(place)
    Option(map.get((scope, tp)))
  }

  def put(place: PsiElement, tp: ScType, value: Seq[ScalaResolveResult]): Unit = {
    val scope = ImplicitSearchScope.forElement(place)
    map.put((scope, tp), value)
  }

  def typeParametersOwners(tp: ScType): Set[ScTypeParametersOwner] = {
    def collectOwners: Set[ScTypeParametersOwner] = {
      var result = Set[ScTypeParametersOwner]()
      tp.recursiveUpdate {
        case TypeParameterType(_, _, _, psiTP) =>
          psiTP.getOwner match {
            case f: ScFunction => result += f
            case _ =>
          }
          (false, tp)
        case _ => (false, tp)
      }
      result
    }

    typeParametersOwnersCache.get(tp) match {
      case null =>
        val owners = collectOwners
        typeParametersOwnersCache.putIfAbsent(tp, owners)
        owners
      case seq => seq
    }
  }

  def clear(): Unit = {
    map.clear()
    typeParametersOwnersCache.clear()
  }
}
