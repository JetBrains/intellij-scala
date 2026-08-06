package org.example

class MyClassInPlatformSpecificModule {
  new MyClassInSharedSources
  new MyJavaClassInSharedSources

  def myMethodInSpecificModule(): Unit = ???
}
