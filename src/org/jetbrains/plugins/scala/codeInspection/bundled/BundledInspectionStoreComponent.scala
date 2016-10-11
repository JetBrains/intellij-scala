package org.jetbrains.plugins.scala.codeInspection.bundled

import java.util
import java.util.Collections

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.project.migration.ImportedLibrariesService
import org.jetbrains.plugins.scala.project.migration.apiimpl.MigrationApiImpl
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

import scala.collection.JavaConverters._
import scala.collection.mutable

/**
  * User: Dmitry.Naydanov
  * Date: 03.10.16.
  * 
  * @see [[org.jetbrains.plugins.scala.project.migration.ImportedLibrariesService]]
  */
class BundledInspectionStoreComponent(project: Project) extends ProjectComponent {
  private var myContainingLibs: util.Map[String, util.ArrayList[String]] = 
    new util.HashMap[String, util.ArrayList[String]]() //we store paths to lib JARs here 
  private val myLoadedInspections = mutable.HashSet[BundledInspectionBase]()
  
  private def isEnabled = ScalaProjectSettings.getInstance(project).isBundledInspectionsSearchEnabled
  
  override def projectClosed(): Unit = {
    serializeInspections()
  }

  override def projectOpened(): Unit = {}

  override def initComponent(): Unit = {
    if (isEnabled) ApplicationManager.getApplication.invokeLater(new Runnable {
      override def run(): Unit = deserializeInspections()
    })
  }

  override def disposeComponent(): Unit = {
  }

  override def getComponentName: String = "ScalaBundledInspectionStoreComponent"
  
  def getLoadedInspections: Seq[BundledInspectionBase] = {
    myLoadedInspections.toSeq
  }
  
  def getFilteredInspections: Seq[BundledInspectionBase] = {
    if (!isEnabled) return Seq.empty
    
    val disabled = ScalaProjectSettings.getInstance(project).getBundledInspectionIdsDisabled
    getLoadedInspections.filter(b => !disabled.contains(b.getId))
  }
  
  def addLoadedInspection(fromJarPath: String, fqn: String, ins: BundledInspectionBase): Unit = {
    val jp = myContainingLibs.get(fromJarPath)
    if (jp == null) {
      val al1 = new util.ArrayList[String]()
      al1.add(fqn)
      myContainingLibs.put(fromJarPath, al1)
    } else jp.add(fqn)
    
    addLoadedInspectionRaw(ins)
  }
  
  def clearLoaded(): Unit = {
    if (myContainingLibs != null) myContainingLibs.clear()
    if (myLoadedInspections != null) myLoadedInspections.clear()
    
    ScalaProjectSettings.getInstance(project).setBundledLibJarsPathsToInspections(Collections.emptyMap())
  }
  
  def completeLoading(): Unit = {
    serializeInspections()
  }
  
  private def addLoadedInspectionRaw(ins: BundledInspectionBase): Unit = myLoadedInspections += ins 
  
  private def serializeInspections() {
    ScalaProjectSettings.getInstance(project).setBundledLibJarsPathsToInspections(myContainingLibs)
  }
  
  private def deserializeInspections() {
    val settings = ScalaProjectSettings.getInstance(project)
    myContainingLibs = new util.HashMap[String, util.ArrayList[String]](settings.getBundledLibJarsPathsToInspections)
    val i = myContainingLibs.entrySet().iterator()

    while (i.hasNext) {
      val kv = i.next()
      ImportedLibrariesService.loadClassFromJar(MigrationApiImpl.getApiInstance(project), kv.getKey,
        kv.getValue.asScala, a => a.asInstanceOf[BundledInspectionBase]).foreach(addLoadedInspectionRaw)
    }
  }
}

object BundledInspectionStoreComponent {
  def getInstance(project: Project) = project.getComponent(classOf[BundledInspectionStoreComponent])
}