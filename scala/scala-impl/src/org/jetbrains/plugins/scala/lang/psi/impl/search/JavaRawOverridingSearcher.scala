package org.jetbrains.plugins.scala.lang.psi.impl.search

import com.intellij.openapi.util.Pair
import com.intellij.psi._
import com.intellij.psi.impl.search.JavaOverridingMethodsSearcher
import com.intellij.psi.impl.source.PsiMethodImpl
import com.intellij.psi.search.searches.{AllOverridingMethodsSearch, OverridingMethodsSearch}
import com.intellij.psi.util.PsiUtil
import com.intellij.util.{Processor, QueryExecutor}
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, PsiMemberExt, PsiTypeExt, inReadAction}
import org.jetbrains.plugins.scala.finder.ScalaFilterScope
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.search.JavaRawOverridingSearcher._
import org.jetbrains.plugins.scala.lang.psi.light.{PsiMethodWrapper, ScFunctionWrapper}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil

/**
  * Nikolay.Tropin
  * 24-May-17
  */

/*
* Raw types from java are viewed as existential types by scalac, but java overrider search doesn't know about that.
* */
class JavaRawOverridingSearcher extends QueryExecutor[PsiMethod, OverridingMethodsSearch.SearchParameters] {
  override def execute(qParams: OverridingMethodsSearch.SearchParameters, consumer: Processor[PsiMethod]): Boolean = {
    val method = qParams.getMethod
    method match {
      case m: PsiMethodImpl if hasRawTypeParam(m) =>
        val cClass = inReadAction(m.getContainingClass)
        if (cClass == null) return true

        val wrapper = rawMethodWrapper(m, cClass)
        val scalaScope = ScalaFilterScope(wrapper.getProject, qParams.getScope)

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
                       consumer: Processor[Pair[PsiMethod, PsiMethod]]): Boolean = {

    val clazz = qParams.getPsiClass
    val potentials = inReadAction {
      clazz.getMethods.collect {
        case m: PsiMethodImpl if hasRawTypeParam(m) && PsiUtil.canBeOverridden(m) => m
      }
    }

    for (superMethod <- potentials) {
      inReadAction {
        val wrapper = rawMethodWrapper(superMethod, clazz)
        val scalaScope = ScalaFilterScope(wrapper.getProject, qParams.getScope)

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
    val methodCopy = inReadAction(m.copy.asInstanceOf[PsiMethod])

    new PsiMethodWrapper(m.getManager, methodCopy, cClass) {

      override protected def returnType: ScType = {
        Option(m.getReturnType).map(_.toScType()).orNull
      }

      override protected def parameterListText: String = {
        val params = m.getParameterList.getParameters.map(withExistentials)
        params.mkString("(", ", ", ")")
      }
    }
  }

  private def withExistentials(p: PsiParameter): String = {
    val paramType: PsiType = p.getType
    if (!isRaw(paramType)) return p.getText

    implicit val pc: ProjectContext = p.projectContext

    val asViewedFromScala = paramType.toScType().toPsiType
    val typeText = asViewedFromScala.getCanonicalText
    s"$typeText ${p.getName}"
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
