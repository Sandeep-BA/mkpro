# FactEngine — Verified Facts & Relationship Validation

The FactEngine provides **mathematically verifiable formulas** and **relationship validation** to mkpro agents, eliminating hallucinated calculations and incorrect technology claims.

## Why?

LLMs are unreliable at:
- **Arithmetic** — "area of circle with r=5 is 50" (wrong: 78.54)
- **Technology relationships** — "HPA works without metrics-server" (wrong: it requires it)
- **Unit conversions** — mixing meters/feet, bits/bytes, C/F

The FactEngine solves this by providing **computable truths** and **verified relationship graphs** that agents can reference before, during, and after task execution.

## Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                         FactEngine                                 │
├──────────────────────────────────────────────────────────────────┤
│                                                                    │
│  ┌─────────────────┐  ┌──────────────┐  ┌─────────────────────┐ │
│  │ FactStore       │  │FactClassifier│  │ GroovyFactEvaluator │ │
│  │                 │  │              │  │                     │ │
│  │ Loads facts.yaml│  │ Keywords →   │  │ Sandboxed Groovy    │ │
│  │ Indexes by      │  │ domain match │  │ verify()/validate() │ │
│  │ keyword+domain  │  │ Zero-latency │  │ 5s timeout          │ │
│  └─────────────────┘  └──────────────┘  └─────────────────────┘ │
│                                                                    │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │ RelationshipGraph + RelationshipValidator                    │ │
│  │                                                               │ │
│  │ • Directed graph (adjacency list with labeled edges)         │ │
│  │ • BFS transitive traversal (A requires B requires C)        │ │
│  │ • Contradiction detection (circular deps, conflicts)         │ │
│  │ • Claim validation ("X without Y" pattern matching)          │ │
│  └─────────────────────────────────────────────────────────────┘ │
│                                                                    │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │ Integration Points                                            │ │
│  │                                                               │ │
│  │ PRE-TURN:  Keyword scan → inject formulas into stimulus      │ │
│  │ POST-TURN: Validate math claims + relationship assertions    │ │
│  │ ON-DEMAND: Agent calls verify_fact tool                      │ │
│  └─────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────┘
```

## Two Verification Engines

### 1. Math Verifier (Groovy Scripts)

Every mathematical fact has an associated Groovy script that can **compute the correct answer**:

```yaml
# facts.yaml
math:
  geometry:
    circle_area:
      formula: "A = π × r²"
      keywords: ["circle", "area", "radius"]
      script: |
        def verify(Map v) {
          def r = v.r as double
          [result: Math.PI * r * r, unit: "sq_units"]
        }
        def validate(Map v) {
          def r = v.r as double
          def claimed = v.A as double
          def expected = Math.PI * r * r
          [correct: Math.abs(claimed - expected) < 0.01, expected: expected, got: claimed]
        }
```

**Usage:**
```
verify_fact(action="verify_math", domain="circle_area", variables='{"r": 5}')
→ {result: 78.54, unit: "sq_units"}

verify_fact(action="validate_math", domain="circle_area", variables='{"r": 5, "A": 50}')
→ {correct: false, expected: 78.54, got: 50.0}
```

### 2. Relationship Validator (Graph Traversal)

Technology relationships stored as subject-predicate-object triples, traversable transitively:

```yaml
relationships:
  kubernetes:
    - {subject: "HPA", predicate: "requires", object: "metrics-server"}
    - {subject: "metrics-server", predicate: "requires", object: "Kubernetes 1.8+"}
```

**Usage:**
```
verify_fact(action="check_relationship", subject="HPA", predicate="requires", object="metrics-server")
→ {verified: true, type: "direct", chain: ["HPA", "metrics-server"]}

verify_fact(action="check_relationship", subject="HPA", predicate="requires", object="Kubernetes 1.8+")
→ {verified: true, type: "transitive", chain: ["hpa", "metrics-server", "kubernetes 1.8+"]}

verify_fact(action="query", subject="Spring Boot 3")
→ {relationships: ["Spring Boot 3 requires Java 17+", "Spring Boot 3 replaces Spring Boot 2"], count: 2}
```

## How Facts Are Triggered (No LLM Needed)

The same mechanism as Markov fast-routing — **keyword scanning**:

```
User: "write a function to calculate sphere volume"
         ↓
FactClassifier: ["sphere", "volume"] → matches "geometry.sphere_volume"
         ↓
Pre-turn injection: "[VERIFIED FACTS] V = (4/3)πr³"
         ↓
Agent has correct formula BEFORE it starts coding
```

| Phase | LLM Call? | Method |
|---|---|---|
| Pre-turn fact injection | No | Keyword scan → direct lookup |
| Post-turn validation | No | Pattern matching + Groovy computation |
| Agent tool call | No | Direct API call with variables |
| Building facts.yaml | Yes (one-time) | LLM extracts from documentation |

## Covered Domains

### Math & Physics (~40 formulas with scripts)

| Domain | Examples |
|---|---|
| **Geometry** | Circle area/circumference, sphere volume/surface area, triangle area, Pythagorean theorem, 2D/3D distance, cylinder/cone volume |
| **Trigonometry** | sin²+cos²=1, law of cosines, radian/degree conversion |
| **Algebra** | Quadratic formula, compound/simple interest, percentage, logarithm base change |
| **Statistics** | Mean, standard deviation |
| **Mechanics** | F=ma, kinetic/potential energy, work, momentum, gravitational force, velocity |
| **Electricity** | Ohm's law, electrical power, energy consumption |
| **Thermodynamics** | Celsius↔Fahrenheit, ideal gas law |
| **CS/Complexity** | Binary search O(log n), linear search O(n), comparison sort O(n log n) |
| **Networking** | Throughput, propagation delay, bandwidth-delay product, transfer time |

### Relationships (~80 triples)

| Domain | Coverage |
|---|---|
| **Kubernetes** | HPA, VPA, metrics-server, Ingress, Pods, Deployments, Helm, Istio |
| **Java** | Spring Boot 2/3, JPA, Hibernate, JUnit, Lombok, Java versions, GraalVM |
| **Networking** | HTTPS, TLS, mTLS, HTTP/2/3, QUIC, WebSocket, gRPC, REST, GraphQL, DNS, CDN |
| **Databases** | PostgreSQL, MongoDB, Redis, Elasticsearch, ACID, BASE, indexes, ORM |
| **DevOps** | Docker, Kubernetes, Terraform, Ansible, CI/CD, Prometheus, Grafana, ELK |
| **Security** | OAuth2, JWT, RBAC, CORS, XSS, SQL injection, CSRF, bcrypt, AES, RSA |
| **Mobile** | Jetpack Compose, SwiftUI, React Native, Flutter, Kotlin, Swift |

## Contradiction Detection

The RelationshipValidator catches logical conflicts:

```
Existing: "TLS 1.3 replaces TLS 1.2"
New claim: "TLS 1.2 replaces TLS 1.3"
→ CONTRADICTION: Cannot have both replacement directions

Existing: "HPA requires metrics-server"
Agent says: "Configure HPA without metrics-server"
→ CONFLICT: HPA requires metrics-server

Existing: "A requires B", "B requires C"
New claim: "C requires A"
→ CIRCULAR DEPENDENCY detected via BFS
```

## Post-Turn Validation Example

```
Agent response: "Spring Boot 2 application uses Java 21 and HPA for 
                 autoscaling without metrics-server. Memory = 2 × 512 × 3 = 2048MB"

FactEngine validates:
  ⚠ Relationship: Spring Boot 2 requires Java 8+, not Java 21 (need Spring Boot 3)
  ⚠ Relationship: HPA requires metrics-server (contradicts "without")
  ⚠ Math: 2 × 512 × 3 = 3072, not 2048
```

## Groovy Script Sandbox

Fact verification scripts run in a sandboxed Groovy environment:

- **Allowed:** `Math.*`, basic arithmetic, collections, control flow
- **Blocked:** Runtime, ProcessBuilder, networking, threads, file I/O
- **Timeout:** 5 seconds max
- **Isolation:** Each evaluation in a fresh binding

Same security model as the existing `ScriptEngine` for agent scripting.

## Agent Tool: `verify_fact`

Available actions:

| Action | Parameters | Returns |
|---|---|---|
| `verify_math` | domain, variables (JSON) | Computed result |
| `validate_math` | domain, variables (JSON with claimed value) | correct: true/false + expected |
| `check_relationship` | subject, predicate, object | verified: true/false + chain |
| `query` | subject | All known relationships |

## Adding New Facts

### Math facts (add to `facts.yaml`):

```yaml
math:
  your_domain:
    your_fact_name:
      formula: "human-readable formula"
      keywords: ["trigger", "keywords"]
      variables: {input: [var1, var2], output: result}
      script: |
        def verify(Map v) {
          // compute correct result from inputs
          [result: computed_value, unit: "unit_name"]
        }
```

### Relationship triples:

```yaml
relationships:
  your_domain:
    - {subject: "Technology A", predicate: "requires", object: "Technology B"}
    - {subject: "Technology B", predicate: "replaces", object: "Technology C"}
```

Supported predicates: `requires`, `replaces`, `extends`, `part_of`, `manages`, `configures`, `alternative_to`, `type`, `provides`, `uses`, `example`, `used_for`, `implements`, `includes`, `prevents`, `improves`, `abstracts`, `orchestrates`, `upgrades_from`.

## File Structure

```
com.mkpro.facts/
├── FactEngine.java            — Orchestrator (pre-turn, post-turn, on-demand)
├── FactStore.java             — YAML loader + keyword indexer
├── FactClassifier.java        — Zero-latency keyword → domain matching
├── GroovyFactEvaluator.java   — Sandboxed Groovy script execution
├── RelationshipGraph.java     — Directed graph with BFS traversal
├── RelationshipValidator.java — Check, transitive check, claim validation
├── VerifyFactTool.java        — Agent-callable BaseTool
├── MathFact.java              — Math fact model
└── RelationshipTriple.java    — S-P-O triple model

src/main/resources/
└── facts.yaml                 — All facts (~40 math + ~80 relationships)
```

## Design Decisions

1. **Groovy for scripts** — Already in the project (sandboxed ScriptEngine exists), pure math needs nothing else.
2. **YAML for facts** — Human-readable, easy to extend, ships in JAR.
3. **Keyword triggering** — Same zero-latency pattern as Markov routing. No LLM call at runtime.
4. **Graph for relationships** — BFS gives transitive inference. Adjacency list is fast for small graphs (~100 nodes).
5. **Separation of math vs. relationships** — Different verification methods (compute vs. traverse), unified through FactEngine orchestrator.
6. **facts.yaml in resources** — Immutable truths bundled with the application. Not user-editable at runtime (unlike schedules.yaml which is mutable).
