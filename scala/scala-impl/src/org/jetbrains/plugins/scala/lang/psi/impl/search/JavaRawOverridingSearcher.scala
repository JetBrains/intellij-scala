package org.jetbrains.plugins.scala.lang.psi.impl.search

import com.intellij.openapi.util.Pair
import com.intellij.psi._
import com.intellij.psi.impl.light.{LightMethod, LightParameter, LightParameterListBuilder}
import com.intellij.psi.impl.search.JavaOverridingMethodsSearcher
import com.intellij.psi.impl.source.PsiMethodImpl
import com.intellij.psi.search.searches.{AllOverridingMethodsSearch, OverridingMethodsSearch}
import com.intellij.psi.util.{MethodSignature, MethodSignatureBackedByPsiMethod, PsiUtil}
import com.intellij.util.{Processor, QueryExecutor}
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, PsiMemberExt, PsiTypeExt, inReadAction}
import org.jetbrains.plugins.scala.finder.ScalaFilterScope
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.search.JavaRawOverridingSearcher._
import org.jetbrains.plugins.scala.lang.psi.light.ScFunctionWrapper
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil

/*
* Raw types from java are viewed as existential types by scalac, but java overrider search doesn't know about that.
* */
class JavaRawOverridingSearcher extends QueryExecutor[PsiMethod, OverridingMethodsSearch.SearchParameters] {
  override def execute(qParams: OverridingMethodsSearch.SearchParameters, consumer: Processor[_ >: PsiMethod]): Boolean = {
    val method = qParams.getMethod
    method match {
      case m: PsiMethodImpl if hasRawTypeParam(m) =>
        val cClass = inReadAction(m.getContainingClass)
        if (cClass == null) return true

        val wrapper = rawMethodWrapper(m, cClass)
        val scalaScope = ScalaFilterScope(qParams.getScope)(wrapper.getProject)

        val newParams = new OverridingMethodsSearch.SearchParameters(wrapper, scalaScope, qParams.isCheckDeep)
        val newProcessor = new Processor[PsiMethod] {
          override def process(t: PsiMethod): Boolean = {
            if (isSuperMethodForScala(m, t)) consumer.process(t)
            else true
          }
        }
        new JavaOverridingMethodsSearcher().execute(newParams, newProcessor)
      case _ =>
        true
    }
  }
}

class JavaRawAllOverridingSearcher extends QueryExecutor[Pair[PsiMethod, PsiMethod], AllOverridingMethodsSearch.SearchParameters] {
  override def execute(qParams: AllOverridingMethodsSearch.SearchParameters,
                       consumer: Processor[_ >: Pair[PsiMethod, PsiMethod]]): Boolean = {

    val clazz = qParams.getPsiClass
    val potentials = inReadAction {
      clazz.getMethods.collect {
        case m: PsiMethodImpl if hasRawTypeParam(m) && PsiUtil.canBeOverridden(m) => m
      }
    }

    for (superMethod <- potentials) {
      inReadAction {
        val wrapper = rawMethodWrapper(superMethod, clazz)
        val scalaScope = ScalaFilterScope(qParams.getScope)(wrapper.getProject)

        val params = new OverridingMethodsSearch.SearchParameters(wrapper, scalaScope, /*checkDeep*/ true)
        val processor = new Processor[PsiMethod] {
          override def process(t: PsiMethod): Boolean = {
            if (isSuperMethodForScala(superMethod, t))
              consumer.process(new Pair(superMethod, t))
            else true
          }
        }
        val continue = new JavaOverridingMethodsSearcher().execute(params, processor)
        if (!continue) return false
      }
    }

    true
  }
}

private[search] object JavaRawOverridingSearcher {
  def hasRawTypeParam(method: PsiMethodImpl): Boolean = inReadAction {
    val parameters = method.getParameterList.getParameters
    parameters.map(_.getType).exists(isRaw)
  }

  def isRaw(t: PsiType): Boolean = t match {
    case ct: PsiClassType => ct.isRaw
    case _ => false
  }

  def rawMethodWrapper(m: PsiMethod, cClass: PsiClass): PsiMethod = {
    new LightMethod(m.getManager, m, cClass) {
      override def getParameterList: PsiParameterList = {
        val lightList = new LightParameterListBuilder(m.getManager, m.getLanguage)
        val originalParams = m.getParameterList.getParameters
        originalParams.foreach(p => lightList.addParameter(asViewedFromScala(p)))
        lightList
      }

      override def getSignature(substitutor: PsiSubstitutor): MethodSignature =
        MethodSignatureBackedByPsiMethod.create(this, substitutor)
    }
  }

  private def asViewedFromScala(p: PsiParameter): PsiParameter = {
    val paramType: PsiType = p.getType
    if (!isRaw(paramType)) return p

    implicit val pc: ProjectContext = p.projectContext
    val typeFromScala = paramType.toScType().toPsiType

    new LightParameter(p.getName, typeFromScala, p.getDeclarationScope, ScalaLanguage.INSTANCE, p.isVarArgs)
  }


  def isSuperMethodForScala(superMethod: PsiMethod, subMethod: PsiMethod): Boolean = {
    val scFun = subMethod match {
      case ScFunctionWrapper(delegate) => delegate
      case fun: ScFunction => fun
      case _ => return false
    }
    inReadAction {
      val superMethodClasses = scFun.superMethods.map(_.containingClass)
      superMethodClasses.exists(ScEquivalenceUtil.areClassesEquivalent(_, superMethod.containingClass))
    }
  }
}
