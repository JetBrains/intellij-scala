package org.jetbrains.plugins.scala
package lang
package psi
package impl

import api.statements.params.ScTypeParam
import com.intellij.openapi.project.Project
import com.intellij.openapi.components.ProjectComponent
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.util.containers.WeakValueHashMap
import java.util.WeakHashMap
import toplevel.synthetic.{SyntheticPackageCreator, ScSyntheticPackage}
import types._
import com.intellij.psi.{PsiClassType, PsiManager, PsiTypeParameter}

class ScalaPsiManager(project: Project) extends ProjectComponent {
  def projectOpened {}
  def projectClosed {}
  def getComponentName = "ScalaPsiManager"
  def disposeComponent {}
  def initComponent {
    PsiManager.getInstance(project).asInstanceOf[PsiManagerEx].registerRunnableToRunOnAnyChange(new Runnable {
      override def run = {
        syntheticPackages.clear
        typeVariables.clear
      }
    })
  }

  private val syntheticPackagesCreator = new SyntheticPackageCreator(project)
  private val syntheticPackages = new WeakValueHashMap[String, Any]

  private val typeVariables = new WeakHashMap[PsiTypeParameter, ScTypeParameterType]

  def syntheticPackage(fqn : String) : ScSyntheticPackage = {
    var p = syntheticPackages.get(fqn)
    if (p == null) {
      p = syntheticPackagesCreator.getPackage(fqn)
      if (p == null) p = Null
      synchronized {
        val pp = syntheticPackages.get(fqn)
        if (pp == null) {
          syntheticPackages.put(fqn, p)
        } else {
          p = pp
        }
      }
    }

    p match {case synth : ScSyntheticPackage => synth case _ => null}
  }

  def typeVariable(tp: PsiTypeParameter) : ScTypeParameterType = {
    var existing = typeVariables.get(tp)
    if (existing != null) existing else {
      import Misc.fun2suspension

      val tv = tp match {
        case stp: ScTypeParam => {
          typeVariables.put(tp, new ScTypeParameterType(stp.name, List.empty, () => Nothing, () => Any, tp)) //to prevent SOE
          val inner = stp.typeParameters.map{typeVariable(_)}.toList
          val lower = () => stp.lowerBound.getOrElse(Nothing)
          val upper = () => stp.upperBound.getOrElse(Any)
          // todo rework for error handling!
          val res = new ScTypeParameterType(stp.name, inner, lower, upper, stp)
          typeVariables.put(tp, res)
          res
        }
        case _ => {
          typeVariables.put(tp, new ScTypeParameterType(tp.getName, List.empty, () => Nothing, () => Any, tp)) //to prevent SOE
          val lower = () => Nothing
          val scType = tp.getSuperTypes match {
            case array: Array[PsiClassType] if array.length == 1 => ScType.create(array(0), project)
            case many => new ScCompoundType(collection.immutable.Seq(many.map{ScType.create(_, project)}.toSeq: _*), Seq.empty, Seq.empty)
          }
          val upper = () => scType
          val res = new ScTypeParameterType(tp.getName, Nil, lower, upper, tp)
          typeVariables.put(tp, res)
          res
        }
      }
      synchronized {
        existing = typeVariables.get(tp)
        if (existing == null) {
          typeVariables.put(tp, tv)
          tv
        } else existing
      }
    }
  }
}

object ScalaPsiManager {
  def instance(project : Project) = project.getComponent(classOf[ScalaPsiManager])

  def typeVariable(tp : PsiTypeParameter): ScTypeParameterType = instance(tp.getProject).typeVariable(tp)
}