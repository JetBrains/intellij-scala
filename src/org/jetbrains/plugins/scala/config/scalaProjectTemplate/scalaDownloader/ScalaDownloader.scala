package org.jetbrains.plugins.scala
package config.scalaProjectTemplate.scalaDownloader

import javax.swing.JComponent

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.download.DownloadableFileSetDescription

/**
 * User: Dmitry Naydanov
 * Date: 11/12/12
 */
object ScalaDownloader {
  /**
   * Shows to user possible downloadable files gotten from `scalaDownloadable.xml` descriptor and downloads selected one 
   * 
   * @param project       Project 
   * @param dialogParent  Parent that creates dialog 
   * @return              Local URL of the downloaded file 
   */
  def download(project: Project, dialogParent: JComponent): String = 
    new GenericScalaArchiveDownloader(this.getClass getResource "scalaDownloadable.xml", "Scala", project, dialogParent){
      override protected def filterFiles(file: DownloadableFileSetDescription): Boolean = 
        (SystemInfo.isWindows && file.getName.endsWith("_Win")) ||  (!SystemInfo.isWindows && !file.getName.endsWith("_Win"))
    }.download()
}
