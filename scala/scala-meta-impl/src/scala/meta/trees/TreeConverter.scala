package scala.meta.trees

import scala.meta.EnvironmentProvider

abstract class TreeConverter extends TreeAdapter
  with TypeAdapter
  with Namer
  with Utils
  with EnvironmentProvider
