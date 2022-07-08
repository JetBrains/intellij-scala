package scala.meta.intellij

import java.net.URL
import scala.reflect.internal.util.ScalaClassLoader.URLClassLoader

class MetaClassLoader(urls: Seq[URL], val incompScala: Boolean = false) extends URLClassLoader(urls, null)
