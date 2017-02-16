package org.jetbrains.sbt.project.structure

import java.io.File

import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.data.Play2ProjectData
import org.jetbrains.sbt.project.structure.Play2Keys.AllKeys._
import org.jetbrains.sbt.structure.Play2Data

import scala.collection.immutable.HashMap

/**
  * @author Nikolay Obedin
  */
// TODO: @dmitry.naydanov: please, refactor Play2 part and then remove this class
object Play2OldStructureAdapter {
  type ProjectId = String

  def apply(newData: Seq[(ProjectId, File, Play2Data)]): Play2ProjectData = {
    val projectKeyValueTriples = newData.toSeq.flatMap {
      case (id, baseDir, data) => extractProjectKeyValue(id, baseDir, data)
    }
    val oldData = projectKeyValueTriples.groupBy(_._2).mapValues(_.map({ case (id, _, v) => (id, v)}))

    Play2ProjectData(avoidSL7005Bug(oldData.mapValues(_.toMap)))
  }

  private def extractProjectKeyValue(id: ProjectId, baseDir: File, data: Play2Data): Seq[(ProjectId, String, ParsedValue[_])] =  {
    val playVersion = data.playVersion.map(v => (PLAY_VERSION, new StringParsedValue(v))).toSeq
    val confDirectory = data.confDirectory.map(d => (PLAY_CONF_DIR, new StringParsedValue(d.getCanonicalPath))).toSeq

    val keyValues = playVersion ++ confDirectory ++ Seq(
      (TEMPLATES_IMPORT, new SeqStringParsedValue(data.templatesImports)),
      (ROUTES_IMPORT, new SeqStringParsedValue(data.routesImports)),
      (SOURCE_DIR, new StringParsedValue(data.sourceDirectory.getCanonicalPath)),
      (PROJECT_URI, new StringParsedValue(baseDir.getCanonicalFile.toURI.toString))
    )

    keyValues.map({ case (k, v) => (id, k.name, v)})
  }

  @inline private def avoidSL7005Bug[K, V](m: Map[K,V]): Map[K, V] = HashMap(m.toSeq:_*)
}
