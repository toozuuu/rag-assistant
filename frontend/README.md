# 🎨 Next-Gen RAG Assistant Frontend

A highly polished, responsive, and animated frontend cockpit built with **React 18**, **Vite**, and **Framer Motion (`motion.dev`)**. Styled entirely using premium **Vanilla CSS** with glassmorphism, radial gradient meshes, and custom scrollbar isolation, it is engineered to deliver a seamless desktop-grade experience.

---

## 🚀 Key Architectural Pillars

### 1. 🎬 High-Fidelity Physics-Based Motion (`motion/react`)
We utilize the modern unified `motion` package to power fluid micro-animations:
* **Gliding Mode Pill**: Toggling between editor views inside the Generative Document Writer uses a shared `layoutId` pill background that smoothly slides with custom Spring physics.
* **Fluid Page Transitions**: Page changes inside `App.jsx` are wrapped in `<AnimatePresence mode="wait">` to transition views seamlessly.
* **Enter/Exit Lists**: Ingested files dynamically slide-in and expand on index success, and slide-out on clear, with robust layout synchronization.
* **Staggered Chat Bubbles**: Conversations mount with physical gravity, sliding upwards in a staggered sequence.

### 2. 🛡️ Token-Refresh Auto-Retry Resiliency
To ensure zero-loss browser reloads, the authentication layer implements self-healing requests:
* **Proactive Auth on Mount**: On initial load or refresh, `App.jsx` unconditionally requests a fresh cryptographic JWT session from the backend to invalidate stale caches.
* **Automatic 401/403 Recovery**: If any request returns unauthorized, the client intercepts the failure, renews the token in the background, and transparently retries the identical request, guaranteeing an uninterrupted session.

### 3. 📐 Viewport-Lock & Scrollbar Isolation
To eliminate double browser scrollbars and maintain clean proportions:
* Parent layout boxes are strictly locked via `overflow: hidden; height: 100vh;`.
* Flexible container structures allocate vertical areas dynamically using `flex: 1; min-height: 0;`.
* Scrolling is isolated strictly to the target directories (like the file hub list or the chat bubble container), providing an app-like dashboard experience.

---

## 📁 Component Directory

* [App.jsx](file:///d:/My%20Projects/rag-assistant/frontend/src/App.jsx): The central conductor. Coordinates workspaces listing states, sidebar drawers, responsive layouts, and background silent authentication pools.
* [ChatWindow.jsx](file:///d:/My%20Projects/rag-assistant/frontend/src/components/ChatWindow.jsx): Implements the contextual assistant chat stream. Features inline citations, expandable thought accordions, and screenshot galleries.
* [DocumentWriter.jsx](file:///d:/My%20Projects/rag-assistant/frontend/src/components/DocumentWriter.jsx): In-context document drafting cockpit. Drives split mode textareas, template guidelines, and multi-format exporters (Print-to-PDF).
* [FileUpload.jsx](file:///d:/My%20Projects/rag-assistant/frontend/src/components/FileUpload.jsx): Ingest hub. Manages drag-and-drop actions, two-row semantic card grids (preventing size/status overlaps), and `localStorage` persistence.

---

## 🛠️ Commands & Scripts

Run commands from the `frontend/` directory.

### Development Mode
Starts the Vite dev server with fast Hot Module Replacement:
```bash
npm run dev
```

### Production Build
Transforms, minimizes, and compiles all assets down to lightweight production chunks (~480KB total size):
```bash
npm run build
```

### Production Deployment (Nginx Proxy)
In Docker production environments, the frontend is deployed behind an **Nginx** reverse proxy to route `/api/` calls seamlessly to the backend container under a single port without triggering CORS locks. The configuration is loaded from [nginx.conf](file:///d:/My%20Projects/rag-assistant/frontend/nginx.conf).
