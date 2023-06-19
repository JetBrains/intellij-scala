package org.jetbrains.plugins.scala.projectHighlighting.local

import com.intellij.openapi.util.TextRange

class SbtCrossBuildProjectHighlightingTest_BackReferencesDisabled extends SbtCrossBuildProjectHighlightingTestBase {

  override def testHighlighting(): Unit = {
    withEnabledBackReferencesFromSharedSources(enabled = false) {
      super.testHighlighting()
    }
  }

  import org.jetbrains.plugins.scala.util.TextRangeUtils.ImplicitConversions.tupleToTextRange

  override protected def filesWithProblems: Map[String, Set[TextRange]] = Map(
    "coreFull/js/src/main/scala/org/example/MyClassInPlatformSpecificModule.scala" -> Set(
      (27, 58), // Declaration is never used
      (130, 154), // Declaration is never used
    ),
    "coreFull/js/src/main/scala/org/example/MyJavaClassInPlatformSpecificModule.java" -> Set(
      (35, 70), // Class 'MyJavaClassInPlatformSpecificModule' is never used
      (189, 217), // Method 'myJavaMethodInSpecificModule()' is never used
    ),
    "coreFull/jvm/src/main/scala/org/example/MyClassInPlatformSpecificModule.scala" -> Set(
      (27, 58), // Declaration is never used
      (130, 154), // Declaration is never used
    ),
    "coreFull/jvm/src/main/scala/org/example/MyJavaClassInPlatformSpecificModule.java" -> Set(
      (35, 70), // Class 'MyJavaClassInPlatformSpecificModule' is never used
      (189, 217), // Method 'myJavaMethodInSpecificModule()' is never used
    ),
    "coreFull/native/src/main/scala/org/example/MyClassInPlatformSpecificModule.scala" -> Set(
      (27, 58), // Declaration is never used
      (130, 154), // Declaration is never used
    ),
    "coreFull/native/src/main/scala/org/example/MyJavaClassInPlatformSpecificModule.java" -> Set(
      (35, 70), // Class 'MyJavaClassInPlatformSpecificModule' is never used
      (189, 217), // Method 'myJavaMethodInSpecificModule()' is never used
    ),
    "coreFull/shared/src/main/scala/org/example/MyClassInSharedSources.scala" -> Set(
      (58, 89), // Cannot resolve symbol MyClassInPlatformSpecificModule
      (125, 160), // Cannot resolve symbol MyJavaClassInPlatformSpecificModule
    ),
    "coreFull/shared/src/main/scala/org/example/MyJavaClassInSharedSources.java" -> Set(
      (103, 134), // Cannot resolve symbol 'MyClassInPlatformSpecificModule'
      (181, 216), // Cannot resolve symbol 'MyJavaClassInPlatformSpecificModule'
    ),
    "corePure/.js/src/main/scala/org/example/MyClassInPlatformSpecificModule.scala" -> Set(
      (27, 58), // Declaration is never used
      (130, 154), // Declaration is never used
    ),
    "corePure/.js/src/main/scala/org/example/MyJavaClassInPlatformSpecificModule.java" -> Set(
      (35, 70), // Class 'MyJavaClassInPlatformSpecificModule' is never used
      (189, 217), // Method 'myJavaMethodInSpecificModule()' is never used
    ),
    "corePure/.jvm/src/main/scala/org/example/MyClassInPlatformSpecificModule.scala" -> Set(
      (27, 58), // Declaration is never used
      (130, 154), // Declaration is never used
    ),
    "corePure/.jvm/src/main/scala/org/example/MyJavaClassInPlatformSpecificModule.java" -> Set(
      (35, 70), // Class 'MyJavaClassInPlatformSpecificModule' is never used
      (189, 217), // Method 'myJavaMethodInSpecificModule()' is never used
    ),
    "corePure/.native/src/main/scala/org/example/MyClassInPlatformSpecificModule.scala" -> Set(
      (27, 58), // Declaration is never used
      (130, 154), // Declaration is never used
    ),
    "corePure/.native/src/main/scala/org/example/MyJavaClassInPlatformSpecificModule.java" -> Set(
      (35, 70), // Class 'MyJavaClassInPlatformSpecificModule' is never used
      (189, 217), // Method 'myJavaMethodInSpecificModule()' is never used
    ),
    "corePure/src/main/scala/org/example/MyClassInSharedSources.scala" -> Set(
      (58, 89), // Cannot resolve symbol MyClassInPlatformSpecificModule
      (125, 160), // Cannot resolve symbol MyJavaClassInPlatformSpecificModule
    ),
    "corePure/src/main/scala/org/example/MyJavaClassInSharedSources.java" -> Set(
      (103, 134), // Cannot resolve symbol 'MyClassInPlatformSpecificModule'
      (181, 216), // Cannot resolve symbol 'MyJavaClassInPlatformSpecificModule'
    )
  )
}
