# Test Case Generation — Approaches

## Overview

Three approaches for generating test cases from exam questions, ordered from simplest to most sophisticated.

---

## Approach 1: Template-Based Generation

**How it works:**
- Define test case templates with placeholders
- Fill in values from a config file or spreadsheet

```java
// Template: Q1aTester.java.template
public static void main(String[] args) {
    String[] inputs = { {{INPUTS}} };
    String[] expected = { {{EXPECTED}} };
    // ... test logic
}
```

```properties
# test-cases.properties
Q1a.inputs="hello", "world", "isogram"
Q1a.expected="true", "false", "true"
```

| Pros | Cons |
|------|------|
| Simple, no dependencies | Manual input/output definition required |

---

## Approach 2: Reference Solution Generation

**How it works:**
1. Instructor provides a **reference solution** (correct implementation)
2. System generates random/edge-case inputs
3. Runs reference solution to get expected outputs
4. Generates test file with those pairs

```
Reference Solution + Generated Inputs → Expected Outputs → Test File
```

**Example Flow:**
```
Input Generator → ["hello", "", "aA", "12321"]
                        ↓
Reference Q1a.java → getIsogramWords() 
                        ↓
Expected Outputs → [true, true, false, true]
                        ↓
Generate Q1aTester.java with these pairs
```

| Pros | Cons |
|------|------|
| Automated output generation | Requires correct reference solution |
| Easy to add edge cases | |

---

## Approach 3: AI-Assisted Generation

**How it works:**
- Feed the question prompt + method signature to an LLM
- LLM generates diverse test cases (edge cases, boundary conditions)
- Optionally validate against reference solution

```
Question: "Write a method that returns true if a word is an isogram..."
Method Signature: boolean getIsogramWords(String word)
                        ↓
LLM generates test cases:
- Empty string → true
- Single char → true  
- "isogram" → true
- "hello" → false (duplicate 'l')
- Case sensitivity: "Aa" → depends on spec...
```

| Pros | Cons |
|------|------|
| Catches edge cases humans might miss | Needs LLM API |
| Very high flexibility | Requires validation |

---

## Recommendation

| Approach | Effort | Flexibility | Suggested For |
|----------|--------|-------------|---------------|
| **Template-Based** | Low | Limited | MVP / known question types |
| **Reference Solution** | Medium | High | Reusable across semesters |
| **AI-Assisted** | High | Very High | Bonus feature / future enhancement |

**For MVP:** Start with **Approach 2** (Reference Solution) — instructor already has the correct code, and you can build a simple input generator for each question type.
