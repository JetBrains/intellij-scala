package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.base.SimpleTestCase

/**
 * @author Alexander Podkhalyuzin
 */

class ProblematicAssignmentAnnotatorTest extends SimpleTestCase {
  final val Header = """
  class A; class B
  object A extends A; object B extends B
  """
}