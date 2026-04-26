package org.example;

public class MyJavaClassInSharedSources {
    static {
        {
            new MyClassInPlatformSpecificModule().myMethodInSpecificModule();
            new MyJavaClassInPlatformSpecificModule().myJavaMethodInSpecificModule();
        }
    }
}
