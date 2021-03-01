package org.jetbrains.plugins.scala.autoImport

import com.intellij.util.indexing.{FileBasedIndexExtension, _}
import com.intellij.util.io.{KeyDescriptor, VoidDataExternalizer}
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.finder.SourceFilterScope
import org.jetbrains.plugins.scala.lang.psi.ScImportsHolder
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.project.ProjectContext

import java.io.{DataInput, DataOutput}
import java.util

final class ImportOrderingIndexer extends FileBasedIndexExtension[String, Void] {
  override val getName: ID[String, Void] = ImportOrderingIndexer.Id

  override def getInputFilter: FileBasedIndex.InputFilter = file => {
    file.isInLocalFileSystem &&
      file.getFileType.is[ScalaFileType]
  }

  override def getIndexer: DataIndexer[String, Void, FileContent] = (inputData: FileContent) => {
    inputData.getPsiFile match {
      case file: ScalaFile =>
        val map = new util.HashMap[String, Void]()
        def scanImportHolder(importStmtHolder: ScImportsHolder): Unit = {
          val stmtIt = importStmtHolder.getImportStatements.iterator
          while (stmtIt.hasNext) {
            val stmt = stmtIt.next()
            val exprIt = stmt.importExprs.iterator
            while (exprIt.hasNext) {
              val expr = exprIt.next()
              expr.qualifier match {
                case Some(qualifier) =>
                  val qualName = qualifier.qualName
                  // map.put(qualifier.qualName, null)

                  // This might index some wrong import-paths because of relative imports,
                  // but it shouldn't be that harmful and speed is more important here
                  val prefix = qualName + "."
                  expr.selectorSet match {
                    case Some(set) =>
                      val selectorsIt = set.selectors.iterator
                      while (selectorsIt.hasNext) {
                        val selector = selectorsIt.next()
                        selector.reference match {
                          case Some(ref) =>
                            map.put(prefix + ref.qualName, null)
                          case None =>
                        }
                      }
                    case None =>
                      expr.reference match {
                        case Some(ref) =>
                          map.put(ref.qualName, null)
                        case None =>
                      }
                  }
                case None =>
              }
            }
          }
        }
        def scanPackaging(pack: ScPackaging): Unit = {
          scanImportHolder(pack)
          pack.packagings.foreach(scanPackaging)
        }

        scanImportHolder(file)
        file.firstPackaging.foreach(scanPackaging)
        map
      case _ => java.util.Collections.emptyMap()
    }
  }

  override def getKeyDescriptor: KeyDescriptor[String] = new KeyDescriptor[String] {
    override def getHashCode(value: String): Int = value.hashCode()
    override def isEqual(val1: String, val2: String): Boolean = val1 == val2
    override def save(out: DataOutput, value: String): Unit = out.writeUTF(value)
    override def read(in: DataInput): String = in.readUTF()
  }

  override def getValueExternalizer: VoidDataExternalizer = VoidDataExternalizer.INSTANCE

  override def dependsOnFileContent: Boolean = true

  override def getVersion: Int = 12
}

object ImportOrderingIndexer {
  val Id: ID[String, Void] = ID.create("ImportOrderingIndexer")

  def qualifierImportCountF(implicit ctx: ProjectContext): String => Int = {
    val project = ctx.project
    val sourcesScope = SourceFilterScope(Seq(ScalaFileType.INSTANCE))(project)
    val fileIndex = FileBasedIndex.getInstance()
    (fqn: String) => {
      val count = fileIndex.getValues(Id, fqn, sourcesScope).size()
      //println(fqn + ": " + count)
      count
    }
  }
}
