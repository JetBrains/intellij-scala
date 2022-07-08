package org.jetbrains.plugins.scala
package debugger.evaluation

import com.intellij.codeInsight.PsiEquivalenceUtil
import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.evaluation.expression.Evaluator
import com.intellij.debugger.impl.{DebuggerManagerListener, DebuggerSession}
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiFile}

import scala.collection.mutable

class ScalaEvaluatorCache
  extends Disposable
    with DebuggerManagerListener {

  private val cachedEvaluators = mutable.HashMap[(PsiFile, Int), mutable.HashMap[PsiElement, Evaluator]]()
  private val cachedStamp = mutable.HashMap[PsiFile, Long]()

  override def sessionDetached(session: DebuggerSession): Unit = {
    clear()
  }

  override def dispose(): Unit = clear()

  def clear(): Unit = {
    cachedEvaluators.values.foreach(_.clear())
    cachedEvaluators.clear()
    cachedStamp.clear()
  }

  def get(position: SourcePosition, element: PsiElement): Option[Evaluator] = {
    if (position == null) return None

    val file = position.getFile
    val offset = position.getOffset
    if (!cachedStamp.get(file).contains(file.getModificationStamp)) {
      cachedStamp(file) = file.getModificationStamp
      cachedEvaluators.view.filterKeys(_._1 == file).toMap.foreach {
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

  def add(position: SourcePosition, element: PsiElement, evaluator: Evaluator): Evaluator = {
    if (position != null) {
      val file = position.getFile
      val offset = position.getOffset
      cachedEvaluators.get((file, offset)) match {
        case Some(map) => map += (element -> evaluator)
        case None =>
          cachedEvaluators += ((file, offset) -> mutable.HashMap(element -> evaluator))
      }
    }
    evaluator
  }
}

object ScalaEvaluatorCache {
  def getInstance(project: Project): ScalaEvaluatorCache =
    project.getService(classOf[ScalaEvaluatorCache])
}
