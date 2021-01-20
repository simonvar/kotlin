// TARGET_BACKEND: JVM
// FILE: A.kt

class CoroutineScope

suspend fun <T> runWithTimeout(
    block: suspend CoroutineScope.() -> T
): T? = null

// FILE: B.kt

suspend fun foo(): Boolean = runWithTimeout {
    false
} ?: true

fun box(): String = "OK"