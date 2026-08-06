package org.jetbrains.plugins.scala.testingSupport.test.specs2

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.plugins.scala.caches.{CachesUtil, cachedInUserData}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScInfixExpr
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.testingSupport.test.structureView.TestNodeProvider

import scala.jdk.CollectionConverters._

/**
 * Mirror of [[org.jetbrains.plugins.scala.testingSupport.test.scalatest.ScalaTestTestLocationsFinder]]
 * for specs2.
 *
 * For every `ScInfixExpr` in the class body that `TestNodeProvider`
 * recognises as a specs2 test (`in`, `>>`, `!`) or scope (`should`,
 * `can`), returns the operation reference — i.e. the `in`/`should`/etc.
 * `ScReferenceExpression` itself. Those refs are what the line marker
 * provider compares against.
 */
object Specs2TestLocationsFinder {

  @RequiresReadLock
  def calculateTestLocations(definition: ScTypeDefinition): Seq[PsiElement] =
    cachedInUserData(
      "Specs2TestLocationsFinder.calculateTestLocations",
      definition,
      CachesUtil.fileModTracker(definition.getContainingFile),
      Tuple1(definition)
    ) {
      definition.extendsBlock.templateBody.toSeq.flatMap { body =>
        PsiTreeUtil.findChildrenOfType(body, classOf[ScInfixExpr]).asScala.toSeq.collect {
          case infix if TestNodeProvider.isSpecs2TestExpr(infix) ||
                        TestNodeProvider.isSpecs2ScopeExpr(infix) =>
            infix.operation
        }
      }
    }
}
