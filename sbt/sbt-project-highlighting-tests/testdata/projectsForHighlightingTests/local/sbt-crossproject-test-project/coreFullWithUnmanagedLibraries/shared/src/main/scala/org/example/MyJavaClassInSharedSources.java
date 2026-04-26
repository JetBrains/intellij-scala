package org.example;

@SuppressWarnings("unused")
public class MyJavaClassInSharedSources {
    static {
        {
            new library_package.LibraryClass().libraryMethod();
        }
    }
}
