package org.jetbrains.sbt.project.structure

import com.intellij.platform.eel.EelDescriptor
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.sbt.RichSeq
import org.jetbrains.sbt.project.data.SbtPlay2ProjectData
import org.jetbrains.sbt.project.structure.Play2Keys.AllKeys.*
import org.jetbrains.sbt.project.structure.data.Play2Data
import org.jetbrains.sbt.project.toPath

import java.nio.file.Path
import scala.collection.immutable.HashMap

// TODO: @dmitry.naydanov: please, refactor Play2 part and then remove this class
object Play2OldStructureAdapter {
  type ProjectId = String

  def apply(newData: Seq[(ProjectId, Path, Play2Data)])(using EelDescriptor): SbtPlay2ProjectData = {
    val projectKeyValueTriples = newData.flatMap {
      case (id, baseDir, data) => extractProjectKeyValue(id, baseDir, data)
    }
    val oldData = projectKeyValueTriples.groupBy(_._2).map {
      case (string, triples) => (string, triples.map(t => (t._1, t._3)))
    }

    SbtPlay2ProjectData(avoidSL7005Bug[String, ProjectId, ParsedValue[?]](oldData))
  }

  private def extractProjectKeyValue(id: ProjectId, baseDir: Path, data: Play2Data)(using EelDescriptor): Seq[(ProjectId, String, ParsedValue[?])] =  {
    val playVersion = data.playVersion.map(v => (PLAY_VERSION, StringParsedValue(v))).toSeq
    val confDirectory = data.confDirectory.map(d => (PLAY_CONF_DIR, StringParsedValue(d.toPath.toCanonicalPath.toString))).toSeq

    val keyValues = playVersion ++ confDirectory ++ Seq(
      (TEMPLATES_IMPORT, SeqStringParsedValue(data.templatesImports.toJavaList)),
      (ROUTES_IMPORT, SeqStringParsedValue(data.routesImports.toJavaList)),
      (SOURCE_DIR, StringParsedValue(data.sourceDirectory.toPath.toCanonicalPath.toString)),
      (PROJECT_URI, StringParsedValue(baseDir.toCanonicalPath.toUri.toString))
    )

    keyValues.map((k, v) => (id, k.name, v))
  }

  //SCL-7005
  @inline private def avoidSL7005Bug[K, A, B](m: Map[K, Seq[(A, B)]]): Map[K, Map[A, B]] = {
    val withMapsValues = m.view.mapValues(_.toMap).toMap
    HashMap(withMapsValues.toSeq*)
  }
}
