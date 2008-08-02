package org.jetbrains.plugins.scala.lang.psi.impl

import api.statements.params.ScTypeParam
import com.intellij.psi.PsiTypeParameter
import com.intellij.util.containers.WeakHashMap
import com.intellij.openapi.project.Project
import com.intellij.openapi.components.ProjectComponent
import types._

class ScalaPsiManager(project: Project) extends ProjectComponent {
  def projectOpened {}
  def projectClosed {}
  def getComponentName = "ScalaPsiManager"
  def disposeComponent {}
  def initComponent {}

  private val typeVariables = new WeakHashMap[PsiTypeParameter, ScTypeVariable]

  def typeVariable(tp: PsiTypeParameter) : ScTypeVariable = {
    val existing = typeVariables.get(tp)
    if (existing != null) existing else {
      val tv = tp match {
        case stp: ScTypeParam => {
          val inner = stp.typeParameters.map{typeVariable(_)}
          new ScTypeVariable(inner, stp.lowerBound, stp.upperBound)
        }
        case _ => {
          val supers = tp.getSuperTypes
          val scalaSuper = supers match {
            case Array(single) => ScType.create(single, project)
            case many => new ScCompoundType(many.map{ScType.create(_, project)}, Seq.empty, Seq.empty)
          }
          new ScTypeVariable(Seq.empty, Nothing, scalaSuper)
        }
      }
      typeVariables.put(tp, tv)
      tv
    }
  }
}

object ScalaPsiManager {
  def instance(project : Project) = project.getComponent(classOf[ScalaPsiManager])

  def typeVariable(tp : PsiTypeParameter) = instance(tp.getProject).typeVariable(tp)
}