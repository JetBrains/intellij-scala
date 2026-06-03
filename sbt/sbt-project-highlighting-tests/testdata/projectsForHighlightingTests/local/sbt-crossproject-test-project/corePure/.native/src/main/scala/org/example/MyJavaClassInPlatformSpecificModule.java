package org.example;

public class MyJavaClassInPlatformSpecificModule {
    static {
        new MyClassInSharedSources();
        new MyJavaClassInSharedSources();
    }

    public void myJavaMethodInSpecificModule() {}
}
