package org.jetbrains.sbt.project

import com.intellij.openapi.externalSystem.model.project.LibraryLevel
import com.intellij.openapi.module.StdModuleTypes
import org.jetbrains.sbt.project.data._

/**
 * @author Nikolay Obedin
 * @since 6/5/15.
 */
object ExternalSystemDsl {

  class Attribute[T](val key: String)
  trait ProjectAttribute
  trait ModuleAttribute
  trait LibraryAttribute

  val name =
    new Attribute[String]("name") with ProjectAttribute with ModuleAttribute with LibraryAttribute
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

  class AttributeMap {
    private var attributes = Map.empty[(Attribute[_], String), Any]
    def get[T](attribute: Attribute[T])(implicit m: Manifest[T]): Option[T] =
      attributes.get((attribute, m.toString)).map(_.asInstanceOf[T])
    def getOrFail[T : Manifest](attribute: Attribute[T]): T =
      get(attribute).getOrElse(throw new Error(s"Value for '${attribute.key}' is not found"))
    def put[T](attribute: Attribute[T], value: T)(implicit m: Manifest[T]): Unit =
      attributes = attributes + ((attribute, m.toString) -> value)
  }

  class AttributeDef[T : Manifest](attribute: Attribute[T], attributes: AttributeMap) {
    def :=(newValue: => T): Unit =
      attributes.put(attribute, newValue)
  }

  class AttributeSeqDef[T](attribute: Attribute[Seq[T]], attributes: AttributeMap)(implicit m: Manifest[Seq[T]]) {
    def +=(newValue: => T): Unit = {
      val newSeq = attributes.get(attribute).getOrElse(Seq.empty) :+ newValue
      attributes.put(attribute, newSeq)
    }
    def ++=(newSeq: => Seq[T]): Unit = {
      val seqConcat = attributes.get(attribute).getOrElse(Seq.empty) ++ newSeq
      attributes.put(attribute, seqConcat)
    }
  }

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

    private val attributes = new AttributeMap

    protected implicit def defineAttribute[T : Manifest](attribute: Attribute[T] with ProjectAttribute): AttributeDef[T] =
      new AttributeDef(attribute, attributes)
    protected implicit def defineAttributeSeq[T](attribute: Attribute[Seq[T]] with ProjectAttribute)(implicit m: Manifest[Seq[T]]): AttributeSeqDef[T] =
      new AttributeSeqDef(attribute, attributes)

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
  }

  abstract class module {
    val typeId: String

    def build: ModuleNode = {
      val node = new ModuleNode(typeId,
        attributes.getOrFail(name),
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
    val typeId = StdModuleTypes.JAVA.getId
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
