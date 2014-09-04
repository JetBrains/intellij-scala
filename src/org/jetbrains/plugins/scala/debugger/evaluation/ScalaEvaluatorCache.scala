package org.jetbrains.plugins.scala
package debugger.evaluation

import com.intellij.codeInsight.PsiEquivalenceUtil
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator
import com.intellij.debugger.impl.{DebuggerManagerAdapter, DebuggerSession}
import com.intellij.debugger.{DebuggerManagerEx, SourcePosition}
import com.intellij.openapi.components.{AbstractProjectComponent, ServiceManager}
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiFile}

import scala.collection.mutable

/**
 * Nikolay.Tropin
 * 2014-06-03
 */
class ScalaEvaluatorCache(project: Project) extends AbstractProjectComponent(project) {

  private val cachedEvaluators = mutable.HashMap[(PsiFile, Int), mutable.HashMap[PsiElement, ExpressionEvaluator]]()
  private val cachedStamp = mutable.HashMap[PsiFile, Long]()

  override def projectOpened() = {
    DebuggerManagerEx.getInstanceEx(project).addDebuggerManagerListener(
      new DebuggerManagerAdapter {
        override def sessionDetached(session: DebuggerSession) = clear()
      }
    )
  }

  def clear() {
    cachedEvaluators.values.foreach(_.clear())
    cachedEvaluators.clear()
  }

  def get(position: SourcePosition, element: PsiElement): Option[ExpressionEvaluator] = {
    val file = position.getFile
    val offset = position.getOffset
    if (cachedStamp.get(file) != Some(file.getModificationStamp)) {
      cachedStamp(file) = file.getModificationStamp
      cachedEvaluators.filterKeys(_._1 == file).foreach {
        case (pos, map) =>
          map.clear()
          cachedEvaluators.remove(pos)
      }
      None
    } else {
      cachedEvaluators.get((file, offset)) match {
        case Some(map) => map.collectFirst {
          case (elem, eval) if PsiEquivalenceUtil.areElementsEquivalent(element, elem) => eval
        }
        case None => None
      }
    }
  }

  def add(position: SourcePosition, element: PsiElement, evaluator: ExpressionEvaluator) = {
    val file = position.getFile
    val offset = position.getOffset
    cachedEvaluators.get((file, offset)) match {
      case Some(map) => map += (element -> evaluator)
      case None =>
        cachedEvaluators += ((file, offset) -> mutable.HashMap(element -> evaluator))
    }
  }
}

object ScalaEvaluatorCache {
  def getInstance(project: Project) = ServiceManager.getService(project, classOf[ScalaEvaluatorCache])
}
