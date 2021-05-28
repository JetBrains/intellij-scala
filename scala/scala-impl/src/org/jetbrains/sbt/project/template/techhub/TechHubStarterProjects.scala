package org.jetbrains.sbt.project.template.techhub

import java.io.{File, IOException}

import com.google.gson.Gson

import scala.util.Try

case class IndexEntry(displayName: String, templateName: String, githubRepo: String, githubUrl: String,
                      downloadUrl: String, summary: String, description: String,
                      keywords: Array[String], parameters: Array[EntryParameters], featured: EntryFeatured) {
  override def toString: String = displayName
}
case class EntryParameters(`type`: String, query: String, displayName: String, defaultValue: String, required: Boolean,
                           pattern: String, format: String)
case class EntryFeatured(scala: Int) // there are other fields, but we don't care

object TechHubStarterProjects {

  private val API_VERSION = "v1"
  private val API_URI = s"https://example.lightbend.com/$API_VERSION/api"
  private val TEMPLATES_ENDPOINT = "all-templates"

  def templateFileName(id: String) = s"$id.zip"

  val dummyEntry: IndexEntry = IndexEntry("","","","","","","",Array.empty, Array.empty, EntryFeatured(0))


  def downloadIndex(): Try[Map[String, IndexEntry]] = {
    val jsonTry = TechHubDownloadUtil.downloadString(s"$API_URI/$TEMPLATES_ENDPOINT", 5000)
    val gson = new Gson

    jsonTry.map { json =>
      val parsed = gson.fromJson(json, classOf[Array[IndexEntry]])
      parsed
        .filter {
          entry => entry.keywords != null &&
          entry.displayName != null &&
          entry.templateName != null &&
          entry.downloadUrl != null &&
          entry.keywords.contains("scala")
        }
        .filterNot { entry => entry.keywords.contains("rp-v1")}
        .map { entry => (entry.templateName, entry) }
        .toMap
    }
  }

  def downloadTemplate(entry: IndexEntry, pathTo: File, name: String, onError: String => Unit): Unit = {
    try {
      // hack to pass required name param when necessary. currently only name param is ever required in the templates
      // _rawArchive=true gives us the template without any sbt launchers and scripts
      val url = s"${entry.downloadUrl}?name=$name&_rawArchive_=true"
      TechHubDownloadUtil.downloadContentToFile(url, pathTo)
    } catch {
      case io: IOException =>
        onError(io.getMessage)
    }
  }
}
