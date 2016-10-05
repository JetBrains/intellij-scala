package org.jetbrains.plugins.scala.codeInspection.bundled

import java.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.project.migration.ImportedLibrariesService
import org.jetbrains.plugins.scala.project.migration.apiimpl.MigrationApiImpl

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
  
  override def projectClosed(): Unit = {
    serializeInspections()
  }

  override def projectOpened(): Unit = {}

  override def initComponent(): Unit = {
    if (ImportedLibrariesService.isEnabled) ApplicationManager.getApplication.invokeLater(new Runnable {
      override def run(): Unit = deserializeInspections()
    })
  }

  override def disposeComponent(): Unit = {
  }

  override def getComponentName: String = "ScalaBundledInspectionStoreComponent"
  
  def getLoadedInspections: Seq[BundledInspectionBase] = {
    myLoadedInspections.toSeq
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
    myContainingLibs.clear()
    myLoadedInspections.clear()
    Option(BundledInspectionsPersistentState getInstance project).foreach(_.getLibToInspectionsNames.clear())
  }
  
  def completeLoading(): Unit = {
    serializeInspections()
  }
  
  private def addLoadedInspectionRaw(ins: BundledInspectionBase): Unit = myLoadedInspections += ins 
  
  private def serializeInspections() {
    Option(BundledInspectionsPersistentState getInstance project).foreach(_ setLibToInspectionsNames myContainingLibs) 
  }
  
  private def deserializeInspections() {
    Option(BundledInspectionsPersistentState getInstance project) foreach {
      serialized =>
        myContainingLibs = new util.HashMap[String, util.ArrayList[String]](serialized.getLibToInspectionsNames)
        val i = myContainingLibs.entrySet().iterator()

        while (i.hasNext) {
          val kv = i.next()
          ImportedLibrariesService.loadClassFromJar(MigrationApiImpl.getApiInstance(project), kv.getKey,
            kv.getValue.asScala, a => a.asInstanceOf[BundledInspectionBase]).foreach(addLoadedInspectionRaw)
        }
    }
  }
}

object BundledInspectionStoreComponent {
  def getInstance(project: Project) = project.getComponent(classOf[BundledInspectionStoreComponent])
}