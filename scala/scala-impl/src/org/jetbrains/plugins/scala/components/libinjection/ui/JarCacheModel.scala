package org.jetbrains.plugins.scala.components.libinjection.ui

import javax.swing.AbstractListModel

import org.jetbrains.plugins.scala.components.libinjection.{InjectorPersistentCache, JarManifest}

/**
  * @author mucianm 
  * @since 15.04.16.
  */
class JarCacheModel(val cache: InjectorPersistentCache) extends AbstractListModel[JarManifest] {
  override def getElementAt(i: Int): JarManifest = cache.cache.values().toArray(Array.empty[JarManifest]).apply(i)

  var modified = false

  override def getSize: Int = cache.cache.size()

  def remove(o: Any, idx: Int): Unit = {
    modified = true
    val manifest = o.asInstanceOf[JarManifest]
    cache.cache.remove(manifest.jarPath)
    fireIntervalRemoved(o, idx, idx)
  }

  def setIgnored(o: Any, idx: Int): Unit = {
    modified = true
    val manifest = o.asInstanceOf[JarManifest]
    cache.cache.put(manifest.jarPath, manifest.copy()(isBlackListed = !manifest.isBlackListed, isLoaded = manifest.isLoaded))
    fireContentsChanged(o, idx, idx)
  }

  def commit(): Unit = cache.saveJarCache()

}
