# Error Prone's compiler-only annotation metadata refers to the JDK compiler API.
# Android instrumentation never evaluates this annotation type.
-dontwarn javax.lang.model.element.Modifier
