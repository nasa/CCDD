# CSV extraction regression tests

`CCDD.CcddCSVDataTest` exercises the actual `CcddCSVHandler` constructor and
`retrieveCSVData` method without opening a GUI or database. Only the unrelated
`CcddMain` service getters are mocked. The production source and bundled
libraries are used; the prebuilt `CCDD.jar` is not used.

The test class requires JUnit 4.13.2 and Mockito Core 4.11.0, including their
transitive dependencies (Hamcrest Core 1.3, Byte Buddy and Byte Buddy Agent
1.12.19, and Objenesis 3.3). Put these test-only jars in a directory outside
the source tree and set `TEST_LIBS` to its absolute path.

From the repository root, with JDK 17 on `PATH`, run in a POSIX shell:

```sh
build_dir=$(mktemp -d)
classpath="CCDD_lib/commons-io-2.6.jar:CCDD_lib/*:$TEST_LIBS/*"
find src -name '*.java' > "$build_dir/sources.txt"
javac -encoding UTF-8 --add-exports java.desktop/sun.swing=ALL-UNNAMED \
  -cp "$classpath" -d "$build_dir" @"$build_dir/sources.txt" \
  tests/CCDD/CcddCSVDataTest.java
java -Djava.awt.headless=true --add-exports java.desktop/sun.swing=ALL-UNNAMED \
  -cp "$build_dir:$classpath" org.junit.runner.JUnitCore CCDD.CcddCSVDataTest
```

With JDK 8, omit both `--add-exports` options. The export is required by the
existing Swing implementation on modular JDKs, not by this change.
The explicit Commons IO 2.6 entry prevents the older duplicate jar in
`CCDD_lib` from taking precedence. No bundled dependency is changed.
A successful run reports 90 tests. Remove the temporary build directory
after reviewing the output.
