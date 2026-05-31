# Fabric Inspector

An enterprise-grade, high-precision **Automated Industrial Vision Inspection System** engineered for the production floors of tier-1 garment manufacturers (such as Shahi, Arvind, and Shell Apparels). 

Fabric Inspector leverages state-of-the-art vision models to automatically analyze garment panels, detect manufacturing discrepancies, extract absolute spatial coordinate boundaries, and generate ISO-compliant audit logs—all while maintaining interactive human-in-the-loop correction and offline-first persistence capabilities.

---

## 🌟 Core System Capabilities

### 1. Advanced Multimodal AI Inspections
* **Generative Vision Core:** Powered by the advanced multimodal Gemini models to analyze woven structures, knits, seams, and finishing.
* **Intelligent Noise Filtering:** Built to ignore intentional fabric designs, print patterns (such as polka dots, stripes, or complex graphics), and normal textural shading.
* **Spatial Localization:** Extracts coordinate boundaries formatted as normalized bounding boxes `[ymin, xmin, ymax, xmax]`.

### 2. Live Telemetry & Intake Hub
* **Multi-Intake Support:** Supports stream simulation with live camera tracking feeds, high-definition camera snapshots, or gallery file uploads.
* **Diagnostic Sample Loader:** Test system calibration on-the-fly using curated garment fabric profiles including clean cotton mesh, denim grease marks, torn linen threads, and stained silk.

### 3. Human-in-the-Loop Supervision (Correction Mode)
* **Interactive Canvas overlays:** Supervisors can toggle **Correction Mode** to drag, resize, add, or delete bounding boxes directly on the viewport.
* **Active Feedback Calibration:** Edits and manual overrides instantly update the local system state and provide contextual corrections to keep predictions aligned with human quality standards.

### 4. Enterprise Compliance & Reporting
* **Industrial Report Generator:** Compiles ISO-compliant quality reports in **PDF**, **Excel (XLSX)**, or **CSV** formats, accompanied by live progress indicators.
* **Offline-First Resilience:** Toggleable SQLite offline storage mode to buffer evaluations locally when network connectivity on the factory floor is unstable.
* **Multilingual Localization:** High-fidelity UI instantly switchable between **English**, **Español**, and **Deutsch** for globally distributed production lines.
* **Security Audit Ledger:** A real-time running terminal console displaying debug telemetry, chronological logs, error highlights, and calibration records.

---

## 🎯 Target Defect Classification Directory

Fabric Inspector is trained and configured to scan visual inputs specifically across three major manufacturing categories:

| Category | Defect Label | Structural / Visual Manifestation |
| :--- | :--- | :--- |
| **FABRIC_ANOMALIES** | `Hole / Tear` | Physical punctures, ripped yarns, or broken knit structures. |
| | `Slub` | Defective thick yarn bunching or slub patterns in the weave. |
| | `Contaminated Thread` | Foreign, colored fibers trapped within the woven fibers. |
| | `Snag` | Pulled thread loops on the surface. |
| **SEWING_DEFECTS** | `Skipped Stitch` | Missing thread loops along a structural seam line. |
| | `Open Seam` | Broken stitches exposing a structural gap between panels. |
| | `Seam Puckering` | Taut, bunched, or wrinkled stitch lines. |
| | `Uneven Stitching` | Wandering, crooked, or misaligned needle paths. |
| | `Wavy Seam` | Stretched out, rippled, or un-tensioned fabric edges. |
| **FINISHING_DEFECTS** | `Oil Spot` | Machine lubricant stains, gear grease marks, or dark oil droplets. |
| | `Dye Stain` | Shading variations, localized dye bleeding, or chemical chemical smudges. |
| | `Uncut Thread` | Hanging or dangling loose thread tails at hems, seams, or borders. |
| | `Iron Burn` | Shiny glaze, scorched yellowing pattern, or press marks. |
| | `Tailor Mark` | Leftover pattern ink marks, chalk marks, or alignment pens. |

---

## 🏗️ Architecture & Technology Stack

The application is engineered with modern, robust Android architecture principles:

* **Jetpack Compose:** Declarative, high-performance UI crafted fully around Material Design 3 (M3).
* **MVVM Architecture:** Clean separation of concerns using declarative state holding via `ViewModel`, `MutableStateFlow`, and lifecycle-aware collectors.
* **Room Database:** SQLite-backed offline persistence engine caching comprehensive historical evaluation records, bounding boxes, metadata, and timestamps.
* **Secrets Gradle Plugin:** Secure environment variable management loading production-grade API credentials via injected native `BuildConfig` structures.
* **Responsive Layouts:** Implements window classification adjustments for fluid use across hand-held mobile devices, industrial tablets, or rugged control tablets.

---

## 🚀 Getting Started

### Prerequisites
* **Android Studio Ladybug** (or newer)
* **SDK Level 26 / Android 8.0** (Minimum Required) or newer (Target SDK 34)
* **Java Development Kit (JDK) 17**

### Setup Environment Secrets
To prevent security leaks, API credentials are never hardcoded in source control. 
1. Open the **Secrets panel in AI Studio** or create a `.env` file in your root workspace.
2. Provide your Gemini credentials in the configuration:
   ```env
   GEMINI_API_KEY=your_secured_genai_api_key_here
   ```

### Standard Gradle Execution
Use standard Gradle tasks to compile, test, or package the application:

* **Build Codebase:**
  ```bash
  gradle assembleDebug
  ```
* **Run Local JVM Unit Tests:**
  ```bash
  gradle :app:testDebugUnitTest
  ```

---

## 🔍 Visual Design Guidelines & Theme

* **Aesthetic Palette:** Styled with a dark corporate slate canvas paired with high-contrast emerald green accents for warnings and precise amber borders. Fits seamless industrial environments.
* **Consistent Padding Grid:** Adheres strictly to an 8dp grid system with generous negative space to ensure operator legibility under low-light floor conditions.
* **Ergonomics & Touch Targets:** All interactive components, buttons, and switches maintain touch target dimensions of `48dp x 48dp` or larger to assure fast operation even when operators wear protective floor gloves.
* **Adaptive Panels:** Utilizes fluid column alignments that adjust dynamically on wider multi-pane dashboard screens.

---

## 📝 Compliance Reporting Protocol

When reports are generated:
1. **PDF Export:** Creates a structured, printable page showing detected anomalies, bounding box details, timestamp signatures, operator name, and compliance badges.
2. **Excel & CSV Export:** Formats tabular inspection ledgers mapping local coordinates, severity classes (Critical, High, Medium, Low), confidence logs, and system diagnostics—suitable for ingestion by central ERP inventory machinery.
