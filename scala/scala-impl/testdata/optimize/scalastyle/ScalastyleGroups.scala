// Notification message: Rearranged imports
import java.io.InputStream
import javax.tools.FileObject
import java.io.{IOException, SequenceInputStream}
import scala.collection.{AbstractIterable, AbstractIterator}
import java.util.Random

class ImportSortingTest {
  type IS = InputStream
  type IOE = IOException
  type SIS = SequenceInputStream
  type AI = AbstractIterable[Int]
  type AIr = AbstractIterator[Int]
  type FO = FileObject
  type R = Random
}
/*
import java.io.{InputStream, IOException, SequenceInputStream}
import java.util.Random

import scala.collection.{AbstractIterable, AbstractIterator}

import javax.tools.FileObject

class ImportSortingTest {
  type IS = InputStream
  type IOE = IOException
  type SIS = SequenceInputStream
  type AI = AbstractIterable[Int]
  type AIr = AbstractIterator[Int]
  type FO = FileObject
  type R = Random
}
*/