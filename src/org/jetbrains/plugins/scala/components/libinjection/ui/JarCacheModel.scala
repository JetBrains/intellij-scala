package org.jetbrains.plugins.scala.components.libinjection.ui

import javax.swing.AbstractListModel

import org.jetbrains.plugins.scala.components.libinjection.{InjectorPersistentCache, JarManifest}

/**
  * @author mucianm 
  * @since 15.04.16.
  */
class JarCacheModel(val cache: InjectorPersistentCache) extends AbstractListModel[JarManifest] {
  import scala.collection.JavaConversions._
  override def getElementAt(i: Int): JarManifest = cache.cache.values().toSeq.get(i)

  override def getSize: Int = cache.cache.size()

  def remove(o: Any, idx: Int): Unit = {
    val manifest = o.asInstanceOf[JarManifest]
    cache.cache.remove(manifest.jarPath)
    fireIntervalRemoved(o, idx, idx)
  }

  def setIgnored(o: Any, idx: Int): Unit = {
    val manifest = o.asInstanceOf[JarManifest]
    cache.cache.update(manifest.jarPath, manifest.copy()(isBlackListed = !manifest.isBlackListed, isLoaded = manifest.isLoaded))
    fireContentsChanged(o, idx, idx)
  }

}
