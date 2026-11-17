# Only for the "qa" test build: the test runner (androidx.test) uses Kotlin classes from the app,
# which release shrinking removes. Keeping them doesn't change what the app's own code does.
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
