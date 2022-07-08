package org.jetbrains.sbt.project.structure

import org.jetbrains.sbt.RichSeq
import org.jetbrains.sbt.project.data.SbtPlay2ProjectData
import org.jetbrains.sbt.project.structure.Play2Keys.AllKeys._
import org.jetbrains.sbt.structure.Play2Data

import java.io.File
import scala.collection.immutable.HashMap

// TODO: @dmitry.naydanov: please, refactor Play2 part and then remove this class
object Play2OldStructureAdapter {
  type ProjectId = String

  def apply(newData: Seq[(ProjectId, File, Play2Data)]): SbtPlay2ProjectData = {
    val projectKeyValueTriples = newData.flatMap {
      case (id, baseDir, data) => extractProjectKeyValue(id, baseDir, data)
    }
    val oldData = projectKeyValueTriples.groupBy(_._2).map {
      case (string, triples) => (string, triples.map(t => (t._1, t._3)))
    }

    SbtPlay2ProjectData(avoidSL7005Bug[String, ProjectId, ParsedValue[_]](oldData))
  }

  private def extractProjectKeyValue(id: ProjectId, baseDir: File, data: Play2Data): Seq[(ProjectId, String, ParsedValue[_])] =  {
    val playVersion = data.playVersion.map(v => (PLAY_VERSION, new StringParsedValue(v))).toSeq
    val confDirectory = data.confDirectory.map(d => (PLAY_CONF_DIR, new StringParsedValue(d.getCanonicalPath))).toSeq

    val keyValues = playVersion ++ confDirectory ++ Seq(
      (TEMPLATES_IMPORT, new SeqStringParsedValue(data.templatesImports.toJavaList)),
      (ROUTES_IMPORT, new SeqStringParsedValue(data.routesImports.toJavaList)),
      (SOURCE_DIR, new StringParsedValue(data.sourceDirectory.getCanonicalPath)),
      (PROJECT_URI, new StringParsedValue(baseDir.getCanonicalFile.toURI.toString))
    )

    keyValues.map({ case (k, v) => (id, k.name, v)})
  }

  //SCL-7005
  @inline private def avoidSL7005Bug[K, A, B](m: Map[K, Seq[(A, B)]]): Map[K, Map[A, B]] = {
    val withMapsValues = m.view.mapValues(_.toMap).toMap
    HashMap(withMapsValues.toSeq:_*)
  }
}
