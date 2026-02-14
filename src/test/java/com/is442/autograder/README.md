# Tests

Basic unit tests for the IS442 Auto-Grading System.

## Running Tests

```bash
./gradlew test
```

Test report: `build/reports/tests/test/index.html`

## Test Files

| Test File | What it tests |
|-----------|---------------|
| `config/AppConfigTest.java` | Config loading from `config.properties`, timeout value, question configs structure |
| `extraction/IdentityResolverTest.java` | Username → name derivation (`ping.lee.2023` → `Ping Lee`), Title Case conversion, Java header parsing |
| `model/StudentSubmissionTest.java` | Score accumulation, anomaly tracking, display name fallback, OrgDefinedId default |

## Adding Tests

1. Create a new test class in the matching package under `src/test/java/com/is442/autograder/`
2. Use JUnit 5 annotations (`@Test`, `@TempDir`, etc.)
3. Run `./gradlew test` to verify

Example:
```java
package com.is442.autograder.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class MyNewTest {

  @Test
  void myTestCase() {
    assertEquals(1 + 1, 2);
  }
}
```
