package org.jetbrains.plugins.scala.base.libraryLoaders

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.impl.VirtualFilePointerManagerImpl
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion

/**
  * @author adkozlov
  */
trait LibraryLoader {
  implicit val module: Module

  def init(implicit version: ScalaSdkVersion): Unit

  def clean(): Unit = {}
}

object LibraryLoader {
  def storePointers(): Unit =
    VirtualFilePointerManager.getInstance match {
      case manager: VirtualFilePointerManagerImpl => manager.storePointers()
    }
}

case class CompositeLibrariesLoader(private val loaders: LibraryLoader*)
                                   (implicit val module: Module) extends LibraryLoader {

  def init(implicit version: ScalaSdkVersion): Unit =
    loaders.foreach(_.init)

  override def clean(): Unit =
    loaders.foreach(_.clean())
}

object CompositeLibrariesLoader {
  def apply(loaders: Array[LibraryLoader])(implicit module: Module): CompositeLibrariesLoader =
    CompositeLibrariesLoader(loaders: _*)
}
