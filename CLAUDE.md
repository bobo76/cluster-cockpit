# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## cluster-cockpit

Single-user local UI for interacting with Kubernetes dev environments via `kubectl` (and later `flux`). Spring Boot serves both API and UI on `localhost`; users launch it with `java -jar`.

See `DISCUSSION.md` for the design history (decisions 1–5).

## Stack

- **Backend:** Java 21, Spring Boot 3.3.x, Lombok
- **Frontend (today):** Thymeleaf fragments + HTMX 2.x, served by the same jar
- **K8s access:** shell out to `kubectl` (parsing `-o json` with Jackson), no client library
- **Distribution:** single jar, opens browser to `localhost:<port>` on startup

Note: the global CLAUDE.md mentions Angular 19 / Playwright / multi-module — that describes a *different* project. This one is intentionally minimal and server-rendered.

## Keep the Angular door open (IMPORTANT)

We chose Thymeleaf+HTMX over Angular for low overhead (Decision 4). We may pivot to Angular later. The cost of keeping that door open is small **if** we hold these three rules. Violations are the kind of thing that quietly accrue and make a future rewrite expensive — treat them as blockers in review.

### Rule 1 — Services stay HTML-free

The `kube` package and any `*Service` class must return structured data (records, lists, primitives). They must NOT:

- Touch Spring `Model`
- Return Thymeleaf fragment names or HTML strings
- Know anything about the rendering layer

Controllers in `web/` are the *only* place HTML/Model concerns live. If a service is tempted to format for display, push the formatting into the controller, a dedicated formatter class, or a derived method on the record.

### Rule 2 — Row/response types are `record`s with JSON-friendly fields

Every "row" or "response" shape exposed by a controller is a Java `record` whose fields would survive Jackson serialization unchanged. No Java-only types (no `Optional` fields, no `Duration` directly — pre-format to `String`), no Thymeleaf-specific helpers.

Example: `PodRow(String namespace, String name, String ready, String status, int restarts, String age, String node)`.

When this becomes a JSON endpoint, `@RestController` + Jackson handles it automatically — no parallel DTO layer needed.

### Rule 3 — No logic in templates

Thymeleaf expressions read pre-shaped fields. They do not compute. Forbidden patterns in templates:

- Method calls beyond field accessors (`obj.field()` is fine; `obj.computeBadge()` is fine only if the method is a thin accessor on the record)
- Nested ternaries
- String concatenation for display logic
- Calls into static helpers for formatting

If a template needs a derived value (status badge, color class, "X of Y" string), add it as a method on the record or build it in the controller. Angular components rendering the same record then bind to the same field.

### What NOT to do preemptively

Optionality is cheap when you hold the rules above. It gets expensive fast when you try to "prepare" for Angular. Do NOT:

- Build a separate DTO layer mirroring records "in case JSON looks different"
- Build JSON endpoints alongside HTML endpoints "just in case"
- Add an `/api` URL prefix to today's controllers — migration is search/replace later
- Pick a state-management story (session vs. client signals) now — decide at pivot time
- Wire CORS, OpenAPI generation, etc. now

## Code conventions

- **Lombok:** `@RequiredArgsConstructor` for DI, `@Getter`/`@Setter` for config properties; avoid field injection.
- **Configuration:** Spring `@ConfigurationProperties` classes under `kube/`, registered via `@EnableConfigurationProperties` on the main class.
- **Records:** prefer over classes for any immutable data shape.
- **Java 21:** use `List.of()`, `list.getFirst()`, switch expressions, pattern matching where natural.
- **No mocking the user's environment:** never modify the user's kubeconfig file; treat it as read-only input.

## HTMX patterns in use

- **Cross-fragment events:** server sets `HX-Trigger: <event>` header; listeners use `hx-trigger="<event> from:body"`. Currently used for `cluster-changed` to reload the pod list when the cluster context switches.
- **Polling without losing focus:** split a fragment into outer (form/controls) and inner (data) parts. The inner div polls with `hx-trigger="every Ns"` + `hx-select="#<inner-id>"` to extract only its own portion of the full response. The form/select is not re-rendered on polls, so an open `<select>` stays open. See `fragments/pod-list.html` for the canonical example.

## Build & test

- `mvn spring-boot:run` — run the app locally (serves UI + API on `localhost:<port>`)
- `mvn -q -DskipTests package` — quick build check
- `mvn clean package` — build the distributable jar (`target/cluster-cockpit-*.jar`); run with `java -jar`
- `mvn clean test` — run tests (when tests exist)
- `mvn test -Dtest=ClassName#methodName` — run a single test (when tests exist)
- No checkstyle configured in this project (yet)

## Files of note

- `DISCUSSION.md` — design decisions and the Lens-inspired three-column layout plan (Decision 5)
- `src/main/resources/application.yml` — cluster connections root, allowed/default namespace
- `src/main/java/com/cockpit/clustercockpit/kube/` — framework-agnostic K8s access layer
- `src/main/java/com/cockpit/clustercockpit/web/` — controllers (Thymeleaf today, may grow JSON endpoints)
