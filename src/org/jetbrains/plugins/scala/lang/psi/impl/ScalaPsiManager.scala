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
import com.intellij.openapi.util.Key

class ScalaPsiManager(project: Project) extends ProjectComponent {
  def projectOpened {}
  def projectClosed {}
  def getComponentName = "ScalaPsiManager"
  def disposeComponent {}
  def initComponent {
    PsiManager.getInstance(project).asInstanceOf[PsiManagerEx].registerRunnableToRunOnAnyChange(new Runnable {
      override def run = {
        syntheticPackages.clear
      }
    })
  }

  private val syntheticPackagesCreator = new SyntheticPackageCreator(project)
  private val syntheticPackages = new WeakValueHashMap[String, Any]

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
    import Misc.fun2suspension
    /*var existing = tp.getUserData(ScalaPsiManager.TYPE_VARIABLE_KEY)
    if (existing != null) existing else {

      val tv = */tp match {
        case stp: ScTypeParam => {
//          tp.putUserData(ScalaPsiManager.TYPE_VARIABLE_KEY, new ScTypeParameterType(stp.name, List.empty, () => Nothing, () => Any, tp)) //to prevent SOE
          val inner = stp.typeParameters.map{typeVariable(_)}.toList
          val lower = () => stp.lowerBound.getOrElse(Nothing)
          val upper = () => stp.upperBound.getOrElse(Any)
          // todo rework for error handling!
          val res = new ScTypeParameterType(stp.name, inner, lower, upper, stp)
//          tp.putUserData(ScalaPsiManager.TYPE_VARIABLE_KEY, res)
          res
        }
        case _ => {
//          tp.putUserData(ScalaPsiManager.TYPE_VARIABLE_KEY, new ScTypeParameterType(tp.getName, List.empty, () => Nothing, () => Any, tp)) //to prevent SOE
          val lower = () => Nothing
          val upper = () => tp.getSuperTypes match {
            case array: Array[PsiClassType] if array.length == 1 => ScType.create(array(0), project)
            case many => new ScCompoundType(collection.immutable.Seq(many.map {
              ScType.create(_, project)
            }.toSeq: _*),
              Seq.empty, Seq.empty, ScSubstitutor.empty)
          }
          val res = new ScTypeParameterType(tp.getName, Nil, lower, upper, tp)
//          tp.putUserData(ScalaPsiManager.TYPE_VARIABLE_KEY, res)
          res
        }
      /*}
      synchronized {
        existing = tp.getUserData(ScalaPsiManager.TYPE_VARIABLE_KEY)
        if (existing == null) {
          tp.putUserData(ScalaPsiManager.TYPE_VARIABLE_KEY, tv)
          tv
        } else existing
      }*/
    }
  }
}

object ScalaPsiManager {
  val TYPE_VARIABLE_KEY: Key[ScTypeParameterType] = Key.create("type.variable.key")

  def instance(project : Project) = project.getComponent(classOf[ScalaPsiManager])

  def typeVariable(tp : PsiTypeParameter): ScTypeParameterType = instance(tp.getProject).typeVariable(tp)
}