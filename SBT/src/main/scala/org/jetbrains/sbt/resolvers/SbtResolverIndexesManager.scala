package org.jetbrains.sbt
package resolvers

import com.intellij.openapi.{Disposable}
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.progress.{Task, ProgressManager, ProgressIndicator}

/**
 * @author Nikolay Obedin
 * @since 7/25/14.
 */

class SbtResolverIndexesManager extends Disposable {

  private val indexes: Seq[SbtResolverIndex] = Seq.empty

  def add(resolver: SbtResolver) = {}

  def find(resolver: SbtResolver) = {}

  def dispose() {}

  def test = ProgressManager.getInstance().run(new Task.Backgroundable(null, "Test"){
    def run(progressIndicator: ProgressIndicator) {
      progressIndicator.setFraction(0.0)
      Thread.sleep(5000)
      progressIndicator.setFraction(1.0)
    }
  })
}

object SbtResolverIndexesManager {
  def getInstance = ServiceManager.getService(classOf[SbtResolverIndexesManager])
}
