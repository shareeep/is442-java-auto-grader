# Tests

This repo currently ships without tests. The setup remains in place so tests can be added later under `src/test/java`.

Suggested setup (when re-enabling tests):

1) Add JUnit 5 dependencies in `build.gradle`:

```gradle
dependencies {
  testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'
  testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
```

2) Keep the JUnit 5 test task:

```gradle
tasks.named('test') {
  useJUnitPlatform()
}
```

3) (Optional but recommended) Add JaCoCo for coverage reports:

```gradle
plugins {
  id 'jacoco'
}

jacoco {
  toolVersion = '0.8.12'
}

tasks.named('jacocoTestReport') {
  dependsOn tasks.named('test')
  reports {
    xml.required = true
    html.required = true
  }
}
```

To run tests once they are added:

```bash
./gradlew test
```
