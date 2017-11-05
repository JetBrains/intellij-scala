package org.jetbrains.plugins.cbt.project.structure

class CbtProjectImporingException(message: String) extends Exception(message)

class CbtParsingBuildInfoXmlException
  extends CbtProjectImporingException(
    """Can not parse build inromation of the project.
      | Please, make sure there is no obstructive output in CBT source code""".stripMargin
  )