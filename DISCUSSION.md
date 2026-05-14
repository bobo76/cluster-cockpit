# cluster-cockpit — Project Discussion

A small local UI to interact with a Kubernetes dev environment via a kubeconfig: list pods, deploy a new version, pause/resume Flux, list PVCs with their attached pods.

## Initial idea

> I want to create a small project that could, with a kubeconfig, connect to a dev environment, list pods, allow to deploy a new version, pause flux, list pvc and what pod is attached, what do you think

**Response:** Very doable — Kubernetes client libraries cover all of that (pod list, deployment image updates, PVC + mounting pod lookup), and Flux suspension is just patching `spec.suspend: true` on the Kustomization/HelmRelease. Main tradeoff is scope: a CLI in Go (using `client-go`) is the fastest path; a Spring Boot + Angular app fits the existing stack better but is heavier.

Open questions:
- Personal dev tool (CLI) or shared team UI (web app)?
- Talk to cluster API directly, or shell out to `kubectl`/`flux`?

## Decision 1 — UI for team use

User chose: **UI, maybe usable by the team**.

## Decision 2 — Direct API vs shelling out

### Shell out to `kubectl` / `flux`
- ✅ Fastest to build — wrap commands, parse `-o json` output
- ✅ Behavior matches what the team does manually
- ✅ Auth "just works" via the kubeconfig the binaries already read
- ❌ Requires those binaries installed where the UI runs
- ❌ Parsing CLI output is more fragile; error handling is string-matching
- ❌ Harder to stream/watch resources (need to poll)
- ❌ Process-per-request doesn't scale well for multi-user

### Direct API (e.g., fabric8 kubernetes-client)
- ✅ Structured objects, typed errors, proper watch/streaming for live pod status
- ✅ One process, scales cleanly
- ✅ Easier to add RBAC/audit logging later
- ❌ More upfront code — reimplements `kubectl rollout`, `flux suspend` (both are simple patches)
- ❌ Flux CRDs (`Kustomization`, `HelmRelease`) need CRD models or generic dynamic client usage

## Decision 3 — Single user, easy local install

User clarified: **no multi-user usage, must be easy to install/run on the user's computer**.

This flips the recommendation → **shell out to `kubectl` / `flux`**:

- Team already has `kubectl` and `flux` installed locally
- Kubeconfig already configured → zero auth code
- No CRD modeling, no client library version pinning against cluster version
- `kubectl ... -o json` provides structured output → parsing is fine
- "Doesn't scale" downside doesn't apply to a single user
- Tradeoff: live updates require polling (~2s) instead of a proper watch stream — acceptable for a dev tool

## Decision 4 — Frontend: Thymeleaf + HTMX instead of Angular

User asked if the frontend could be server-rendered to avoid Angular.

Chosen: **Thymeleaf + HTMX**.

- ✅ No Node toolchain, no `ng build`, no separate frontend module
- ✅ HTMX attributes (`hx-get`, `hx-trigger="every 2s"`) handle polling/partial updates without JS
- ✅ Single jar stays trivially simple
- ✅ Cluster is the source of truth — no real need for client-side state
- ❌ Less rich interactivity than Angular (acceptable for this tool)

Alternatives considered: plain Thymeleaf (too clunky for live pod list), Vaadin Flow (heavier, unfamiliar).

## Proposed shape

- **Backend:** Spring Boot (matches existing stack), single jar, runs on `localhost`
- **Frontend:** Thymeleaf templates + HTMX, served by the same jar
- **Distribution:** `java -jar cluster-cockpit.jar` → opens browser to `localhost:<port>`

### Commands to wrap

| Feature | Command |
|---|---|
| Pick cluster | `kubectl config get-contexts` |
| List pods | `kubectl get pods -n <ns> -o json` |
| List PVCs + attached pod | `kubectl get pvc -n <ns> -o json` + `kubectl get pods -o json`, join on `spec.volumes[].persistentVolumeClaim.claimName` |
| Deploy new version | `kubectl set image deployment/<name> <container>=<image> -n <ns>` |
| Pause Flux | `flux suspend kustomization <name> -n <ns>` |
| Resume Flux | `flux resume kustomization <name> -n <ns>` |

## Next step

Scaffold the project (Spring Boot + Thymeleaf + HTMX, single-jar distribution).

## Decision 5 — Lens-inspired three-column layout

User asked for inspiration from Lens (Kubernetes IDE). Adopting a three-column shell.

```
┌────┬──────────────────┬──────────────────────────────────────────┐
│ C1 │ aks-ux-teams-    │  Pods                3 items   [ns: …▾] │
│ C2 │  dev-002      ▾  │  ─────────────────────────────────────── │
│ C3*│                  │  ☐ Name        Namespace   Ready  …      │
│ C4 │ ⚙ Cluster        │  ☐ doms-…      vibes-…      1/1   …      │
│    │ ▾ Workloads      │  ☐ opl-svc-…   vibes-…      1/1   …      │
│    │   • Pods         │                                          │
│    │   Deployments    │                                          │
│    │ ▾ Storage        │                                          │
│    │   PVCs           │                                          │
│    │ ▾ Flux           │                                          │
│    │   Kustomizations │                                          │
│    │   HelmReleases   │                                          │
└────┴──────────────────┴──────────────────────────────────────────┘
   col 1            col 2                       col 3
   cluster rail     resource nav                content
   (~56 px)         (~220 px)                   (fluid)
```

### Columns

1. **Cluster rail (left, narrow)** — one tile per kubeconfig context, current one highlighted. Click = `POST /connections/select`. Replaces the current dropdown. Tile shows a short label (first chars of the context) + tooltip with the full name.
2. **Resource nav (middle)** — collapsible groups: *Workloads* (Pods, Deployments), *Storage* (PVCs), *Flux* (Kustomizations, HelmReleases). Selected entry drives the right pane.
3. **Content pane (right)** — header with page title, item count, and a namespace combobox pinned right (matches Lens). Below: the resource table with row actions.

### Tabs row above content

Lens shows a tab bar at the top of the content pane (*Overview / Pods / Deployments / …*) as a shortcut between sibling resources. **Skipped in v1** — the nav already provides navigation; revisit if duplication helps muscle memory.

### State model

- **Selected cluster** — already in `ClusterConnectionService` (session-scoped).
- **Selected namespace** — currently per-request; **promote to session** so it persists across nav clicks. Single `<select>` lives in the content header; choice survives switching from Pods → PVCs → Deployments.
- **Selected page** — URL-driven: `/pods`, `/deployments`, `/pvcs`, `/flux/kustomizations`, `/flux/helmreleases`. Nav links use `hx-get` with `hx-target="#content"` and `hx-push-url="true"`.

### Per-page actions

| Page                | Row actions                            | Command |
|---------------------|----------------------------------------|---------|
| Pods                | (view-only for now)                    | — |
| Deployments         | `Set image…` (modal with container picker if >1) | `kubectl set image deployment/<n> <container>=<image> -n <ns>` |
| PVCs                | (view + `Mounted by` column)           | join PVCs ↔ pods on `spec.volumes[].persistentVolumeClaim.claimName` |
| Flux Kustomization  | `Suspend` / `Resume`                   | `kubectl patch kustomization/<n> -n <ns> --type=merge -p '{"spec":{"suspend":true|false}}'` |
| Flux HelmRelease    | `Suspend` / `Resume`                   | same patch on `helmrelease/<n>` |

Destructive actions (Set image, Suspend, Resume) require a confirm dialog showing the exact target.

### Polling

- Each content page owns its own poll on the inner table div (`hx-trigger="every 5s"` + `hx-select="#…-table"`), same pattern as the current Pods page so the namespace combobox isn't blown away.
- Cluster rail and resource nav don't poll — they're driven by user navigation and `HX-Trigger: cluster-changed` events.

### Implementation order

1. **Shell refactor** — `index.html` becomes the 3-column layout; current Pods view moves into the `/pods` content slot. Namespace selector moves into the content header and is promoted to session scope. Nav links wired with `hx-get` + `hx-target="#content"` + `hx-push-url`.
2. **Cluster rail fragment** — replace the dropdown with tiles.
3. **Deployments page** — list + `Set image…` modal.
4. **PVCs page** — list + `Mounted by` column.
5. **Flux pages** — Kustomizations + HelmReleases, each with Suspend/Resume.

## Decision 6 — Pod detail panel

Lens-style side panel showing container details for a selected pod (init + regular containers in one panel). Triggered by a dedicated icon button on each pod row (not whole-row click — keeps the row safe for future select/checkbox semantics).

### Data shape (records, JSON-friendly — Rules 1 & 2)

- `PodDetail(String namespace, String name, List<ContainerDetail> initContainers, List<ContainerDetail> containers)`
- `ContainerDetail(String name, String image, String status, List<EnvVar> env, List<MountInfo> mounts)`
- `EnvVar(String name, String value, String source)` — `value` is the literal from `env[].value`; `source` is the pre-formatted reference (e.g. `"from secret postgres-keycloak/password"`) when `env[].valueFrom` is set. **Exactly one of `value` / `source` is populated.**
- `MountInfo(String path, String source, boolean readOnly)`
- `status` is pre-formatted into a single display string in the service (e.g. `"terminated, ready - Completed (exit code: 0)"`) so templates don't compute (Rule 3).

### Secret / ConfigMap values: hide

Show only the reference for `valueFrom` env entries — do **not** fetch and decode the actual Secret/ConfigMap value. Rationale:

- Avoids extra `kubectl get secret` calls per panel open
- Avoids displaying sensitive material in a tool that has no auth in front of it
- The reference (`from secret X key Y`) is what an operator needs to debug wiring; if they need the value, `kubectl get secret` is one command away

Lens shows literal values; we deliberately diverge.

### Trigger UX

Per-row icon button (eye/info icon) in the pod list:

```html
<button hx-get="/pods/{ns}/{name}/detail"
        hx-target="#pod-detail-panel"
        hx-swap="innerHTML">…</button>
```

`#pod-detail-panel` is a fixed-position side drawer in `index.html` (not a third column in the Lens layout — keeps Decision 5 intact). Empty by default; close button clears it.

### Backend pieces

1. `kube/PodDetail.java`, `ContainerDetail`, `EnvVar`, `MountInfo` — new records.
2. `PodService#getPodDetail(connection, namespace, name)` — shells out via `KubectlRunner` (`kubectl get pod <name> -n <ns> -o json`), maps to `PodDetail`. Status string built from `status.containerStatuses[].state` and `status.initContainerStatuses[].state`.
3. `web/PodController` — `GET /pods/{namespace}/{name}/detail` returns fragment.

### Template

`fragments/pod-detail.html` — sections: header (pod name, close button), Init Containers (loop), Containers (loop). Each container renders Status / Image / Environment / Mounts as field reads only. `EnvVar` rendered as `name : value` or `name : source` — template picks whichever is non-null (single ternary, allowed).

### Out of scope for this iteration

- Logs / exec / edit / delete icons shown in the Lens header
- Container resource limits, probes, ports
- Live polling of the detail panel — open it again to refresh
