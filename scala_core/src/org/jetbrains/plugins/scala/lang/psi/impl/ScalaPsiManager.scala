package org.jetbrains.plugins.scala.lang.psi.impl

import api.statements.params.ScTypeParam
import com.intellij.openapi.project.Project
import com.intellij.openapi.components.ProjectComponent
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.{PsiManager, PsiTypeParameter}
import com.intellij.util.containers.WeakValueHashMap
import java.util.WeakHashMap
import toplevel.synthetic.{SyntheticPackageCreator, ScSyntheticPackage}
import types._

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
          val inner = stp.typeParameters.map{typeVariable(_)}.toList
          val lower = () => stp.lowerBound
          val upper = () => stp.upperBound
          val res = new ScTypeParameterType(stp.name, inner, lower, upper, stp)
          typeVariables.put(tp, res)
          res
        }
        case _ => {
          val lower = () => Nothing
          val upper = () => tp.getSuperTypes match {
            case Array(single) => ScType.create(single, project)
            case many => new ScCompoundType(many.map{ScType.create(_, project)}, Seq.empty, Seq.empty)
          }
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

  def typeVariable(tp : PsiTypeParameter) = instance(tp.getProject).typeVariable(tp)
}