package org.jetbrains.plugins.scala.caches

import java.util.concurrent.atomic.AtomicLong

import com.intellij.openapi.util.ModificationTracker

/**
  * @author Nikolay.Tropin
  */

trait ScalaSmartModificationTracker extends ModificationTracker {

  private val counter = new AtomicLong(0L)

  def parentTracker: Option[ModificationTracker] = None

  override def getModificationCount: Long = parentTracker.map(_.getModificationCount).getOrElse(0L) + counter.get()

  def incModCounter(): Unit = counter.incrementAndGet()

  def onPsiChange(): Unit = {}
}

object ScalaSmartModificationTracker {
  val EVER_CHANGED = new ScalaSmartModificationTracker {
    override def getModificationCount: Long = ModificationTracker.EVER_CHANGED.getModificationCount
  }
}









