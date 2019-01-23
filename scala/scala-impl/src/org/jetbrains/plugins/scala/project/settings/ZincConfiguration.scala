package org.jetbrains.plugins.scala.project.settings

import java.util

import com.intellij.openapi.components.{PersistentStateComponent, ServiceManager, State, Storage}
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.{XmlSerializer, XmlSerializerUtil}
import org.jdom.Element

import scala.beans.BeanProperty

@State(
  name = "ZincConfiguration",
  storages = Array(new Storage("zinc.xml"))
)
class ZincConfiguration extends PersistentStateComponent[Element] {

  @BeanProperty var compileToJar: Boolean = false

  @BeanProperty var enableIgnoringScalacOptions: Boolean = true
  @BeanProperty var ignoredScalacOptions: java.util.List[String] = new util.ArrayList[String](util.Arrays.asList(
    "-Xdev",
    "-Xprint:.*",
    "-Xlog-.*",
    "-Ylog:.*",
    "-Ybrowse:.*",
    "-Ycheck:.*",
    "-Yshow:.*",
    "-Ystop-.*",
    "-Ydebug",
    "-Ymacro-debug-.*",
    "-Ystatictics:.*",
    "-Yprofile-.*",
    "-Ygen-asmp .*",
    "-g:vars",
    "-target:jvm-1.8",
    "-encoding .*",
    "-Xmax-classfile-name 255",
    "-Xmaxerrs 100",
    "-Xmaxwarns 100",
    "-Ybackend-parallelism 1",
    "-Ybackend-worker-queue 0",
    "-Yjar-compression-level -1"
  ))

  override def getState: Element = XmlSerializer.serialize(this)

  override def loadState(t: Element): Unit = {
    val loaded = XmlSerializer.deserialize(t, classOf[ZincConfiguration])
    XmlSerializerUtil.copyBean(loaded, this)
  }

}

object ZincConfiguration {
  def instanceIn(project: Project): ZincConfiguration =
    ServiceManager.getService(project, classOf[ZincConfiguration])
}