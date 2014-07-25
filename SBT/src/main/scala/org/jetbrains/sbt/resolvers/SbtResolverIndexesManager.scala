package org.jetbrains.sbt
package resolvers

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.ServiceManager

/**
 * @author Nikolay Obedin
 * @since 7/25/14.
 */

class SbtResolverIndexesManager extends Disposable {

  private val indexes: Seq[SbtResolverIndex] = Seq.empty

  def add(resolver: SbtResolver) = {}

  def find(resolver: SbtResolver) = {}

  def dispose() {}
}

object SbtResolverIndexesManager {
  def getInstance = ServiceManager.getService(classOf[SbtResolverIndexesManager])
}
