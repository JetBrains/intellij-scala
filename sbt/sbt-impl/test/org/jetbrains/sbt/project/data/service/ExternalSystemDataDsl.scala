package org.jetbrains.sbt
package project.data.service

import com.intellij.openapi.externalSystem.model.project.LibraryLevel
import com.intellij.openapi.module.StdModuleTypes
import org.jetbrains.sbt.project.data._

import java.net.URI
import scala.language.implicitConversions

/**
 * DSL for building External System DataNodes in runtime.
 *
 * Example usage:
 *
 *   val testProject = new project {
 *     name := "Some name"
 *     ...
 *
 *     val library1 := new library {
 *       name := "Some library name"
 *     }
 *     val library2 := new library {
 *       name := "Another library name"
 *     }
 *     libraries ++= Seq(library1, library2)
 *
 *     val module1 = new module {
 *       name := "Some module name"
 *       ...
 *       libraryDependencies += library1
 *     }
 *     modules += module1
 *   }
 *   val testProjectDataNode = testProject.build.toDataNode
 *
 * More examples are in test cases in org.jetbrains.sbt.project package
 *
 * See also [[org.jetbrains.sbt.project.ProjectStructureDsl]]
 */
object ExternalSystemDataDsl {

  import DslUtils._

  trait ProjectAttribute
  trait ModuleAttribute
  trait LibraryAttribute

  val name =
    new Attribute[String]("name") with ProjectAttribute with ModuleAttribute with LibraryAttribute
  val projectId =
    new Attribute[String]("projectId") with ProjectAttribute with ModuleAttribute
  val projectURI =
    new Attribute[URI]("projectUri") with ProjectAttribute with ModuleAttribute
  val ideDirectoryPath =
    new Attribute[String]("ideDirectoryPath") with ProjectAttribute
  val linkedProjectPath =
    new Attribute[String]("linkedProjectPath") with ProjectAttribute
  val moduleFileDirectoryPath =
    new Attribute[String]("moduleFileDirectoryPath") with ModuleAttribute
  val externalConfigPath =
    new Attribute[String]("externalConfigPath") with ModuleAttribute
  val libraries =
    new Attribute[Seq[library]]("libraries") with ProjectAttribute with ModuleAttribute
  val modules =
    new Attribute[Seq[module]]("modules") with ProjectAttribute
  val moduleDependencies =
    new Attribute[Seq[module]]("moduleDependencies") with ModuleAttribute
  val libraryDependencies =
    new Attribute[Seq[library]]("libraryDependencies") with ModuleAttribute

  val arbitraryNodes =
    new Attribute[Seq[Node[_]]]("arbitraryNodes") with ProjectAttribute with ModuleAttribute with LibraryAttribute

  class project {

    def build: ProjectNode = {
      val node = new ProjectNode(
        attributes.getOrFail(name),
        attributes.getOrFail(ideDirectoryPath),
        attributes.getOrFail(linkedProjectPath))

      val moduleToNode = {
        val allModules = attributes.get(modules).getOrElse(Seq.empty)
        allModules.map(m => (m, m.build)).toMap
      }

      val libraryToNode = {
        val allLibraries = attributes.get(libraries).getOrElse(Seq.empty)
        allLibraries.map(l => (l, l.build)).toMap
      }

      createModuleDependencies(moduleToNode)
      createLibraryDependencies(moduleToNode, libraryToNode)
      node.addAll(moduleToNode.values.toSeq)
      node.addAll(libraryToNode.values.toSeq)
      attributes.get(arbitraryNodes).foreach(node.addAll)
      node
    }

    private def createModuleDependencies(moduleToNode: Map[module, ModuleNode]): Unit =
      moduleToNode.foreach { case (module, moduleNode) =>
        module.getModuleDependencies.foreach { dependency =>
          moduleToNode.get(dependency).foreach { dependencyModuleNode =>
            moduleNode.add(new ModuleDependencyNode(moduleNode, dependencyModuleNode))
          }
        }
      }

    private def createLibraryDependencies(moduleToNode: Map[module, ModuleNode], libraryToNode: Map[library, LibraryNode]): Unit =
      moduleToNode.foreach { case (module, moduleNode) =>
        module.getLibraryDependencies.foreach { dependency =>
          libraryToNode.get(dependency).foreach { libraryNode =>
            moduleNode.add(new LibraryDependencyNode(moduleNode, libraryNode, LibraryLevel.PROJECT))
          }
        }
      }

    private val attributes = new AttributeMap

    protected implicit def defineAttribute[T : Manifest](attribute: Attribute[T] with ProjectAttribute): AttributeDef[T] =
      new AttributeDef(attribute, attributes)
    protected implicit def defineAttributeSeq[T](attribute: Attribute[Seq[T]] with ProjectAttribute)(implicit m: Manifest[Seq[T]]): AttributeSeqDef[T] =
      new AttributeSeqDef(attribute, attributes)
  }

  abstract class module {
    val typeId: String

    def build: ModuleNode = {
      val node = new ModuleNode(
        typeId,
        attributes.getOrFail(projectId),
        attributes.getOrFail(name),
        attributes.getOrFail(moduleFileDirectoryPath),
        attributes.getOrFail(externalConfigPath)
      )
      attributes.get(libraries).foreach { libs =>
        libs.map(_.build).foreach { libNode =>
          node.add(libNode)
          node.add(new LibraryDependencyNode(node, libNode, LibraryLevel.MODULE))
        }
      }
      attributes.get(arbitraryNodes).foreach(node.addAll)
      node
    }

    def getModuleDependencies: Seq[module] =
      attributes.get(moduleDependencies).getOrElse(Seq.empty)

    def getLibraryDependencies: Seq[library] =
      attributes.get(libraryDependencies).getOrElse(Seq.empty)

    private val attributes = new AttributeMap

    protected implicit def defineAttribute[T : Manifest](attribute: Attribute[T] with ModuleAttribute): AttributeDef[T] =
      new AttributeDef(attribute, attributes)
    protected implicit def defineAttributeSeq[T](attribute: Attribute[Seq[T]] with ModuleAttribute)(implicit m: Manifest[Seq[T]]): AttributeSeqDef[T] =
      new AttributeSeqDef(attribute, attributes)
  }

  class javaModule extends module {
    override val typeId: String = StdModuleTypes.JAVA.getId
  }

  class library {
    def build: LibraryNode = {
      val node = new LibraryNode(attributes.getOrFail(name), true)
      attributes.get(arbitraryNodes).foreach(node.addAll)
      node
    }

    private val attributes = new AttributeMap

    protected implicit def defineAttribute[T : Manifest](attribute: Attribute[T] with LibraryAttribute): AttributeDef[T] =
      new AttributeDef(attribute, attributes)
    protected implicit def defineAttributeSeq[T](attribute: Attribute[Seq[T]] with LibraryAttribute)(implicit m: Manifest[Seq[T]]): AttributeSeqDef[T] =
      new AttributeSeqDef(attribute, attributes)
  }
}
