package org.example

class MyClassInSharedSources {
  new MyClassInPlatformSpecificModule().myMethodInSpecificModule()
  new MyJavaClassInPlatformSpecificModule().myJavaMethodInSpecificModule()
}
