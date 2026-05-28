# 🚀 AI-Powered RAG Assistant with Screenshot Integration

A highly secure, offline-first, and **multimodal-ready RAG** (Retrieval-Augmented Generation) assistant. Engineered with **Spring Boot 3 (Spring AI)**, **React 18 (Vite)**, and a **Qdrant Vector Database**, it runs entirely locally using **Ollama**. The application stands out by extracting both plain text and embedded illustrations, screenshots, and diagrams from documents, letting the AI ground its responses with high fidelity and display visual evidence alongside answers.

---

## 🏗️ System Architecture

This system is built as a three-tier local architecture. It processes, embeds, indexes, and queries documents completely within your machine's perimeter.

```mermaid
graph TD
    %% Styling
    classDef default fill:#1a1b26,stroke:#7aa2f7,stroke-width:2px,color:#a9b1d6;
    classDef frontend fill:#1f2335,stroke:#7dcfff,stroke-width:2px,color:#7dcfff;
    classDef backend fill:#1f2335,stroke:#9ece6a,stroke-width:2px,color:#9ece6a;
    classDef database fill:#1f2335,stroke:#e0af68,stroke-width:2px,color:#e0af68;
    classDef ollama fill:#1f2335,stroke:#f7768e,stroke-width:2px,color:#f7768e;

    subgraph Client["Client Tier (React & Vite)"]
        UI["React Web App (Glassmorphism Layout)"]:::frontend
    end

    subgraph Server["Application Tier (Spring Boot 3)"]
        Controller["Upload & Chat Controllers"]:::backend
        Extractor["Document Processing Suite <br/>(Apache Tika + PDFBox / POI)"]:::backend
        AIService["Spring AI Integration Layer"]:::backend
        ImgStore["Local Image Upload Store"]:::backend
    end

    subgraph DataInf["Data & Inference Tier (100% Offline)"]
        VectorDB[("Qdrant Vector Database")]:::database
        OllamaLLM["Ollama Service <br/>(phi3:mini / nomic-embed)"]:::ollama
    end

    %% Ingestion Flow
    UI -->|1. Drag-and-Drop Documents| Controller
    Controller -->|2. Ingest stream| Extractor
    Extractor -->|3a. Extract plain text chunks| AIService
    Extractor -->|3b. Carve embedded screenshots/images| ImgStore
    AIService -->|4. Generate Vector Embeddings| OllamaLLM
    AIService -->|5. Index text + payload metadata| VectorDB
    ImgStore -.->|Link paths in payload| VectorDB

    %% RAG Query Flow
    UI -->|6. Ask Natural Language Query| Controller
    Controller -->|7. Forward prompt| AIService
    AIService -->|8. Fetch relevant document chunks| VectorDB
    VectorDB -->|9. Returns top matches & image paths| AIService
    AIService -->|10. Feed query + context to local LLM| OllamaLLM
    OllamaLLM -->|11. Generate grounded answer| AIService
    AIService -->|12. Return structured JSON answer with sources and images| UI
```

---

## 🛠️ Why This Technology Stack?

Every element in this architecture is selected to deliver maximum privacy, lightning-fast processing, and enterprise-grade extensibility on consumer-grade hardware.

| Technology | Role | Why We Selected It |
| :--- | :--- | :--- |
| **Spring Boot 3 & Spring AI** | Backend Framework | Spring Boot 3 brings unmatched type-safety, rapid dependency injection, and native compilation capabilities to enterprise Java. **Spring AI** abstracts vector store operations, prompt engineering, and LLM integrations cleanly, allowing us to swap components or models with zero changes to core business logic. |
| **React 18 & Vite** | Frontend Interface | Vite delivers instantaneous Hot Module Replacement (HMR) and highly optimized production builds. React 18 allows us to create a premium, responsive glassmorphism UI with smooth asynchronous states during heavy document uploads and real-time streaming chat rendering. |
| **Qdrant Vector Database** | Vector Indexing | Written in Rust, Qdrant is an ultra-fast vector database engineered for production. It allows us to perform high-speed cosine similarity searches, and seamlessly supports complex payload filtering (allowing us to bind document metadata, sections, and extracted screenshot paths directly to the text vectors). |
| **Ollama (`phi3:mini` & `nomic-embed-text`)** | Local Inference | Ollama runs AI models locally on your CPU/GPU. **`nomic-embed-text`** provides high-quality 8192 token-context embeddings, and **`phi3:mini`** is an exceptionally powerful, lightweight 3.8B parameter instruct model. This ensures **100% data privacy** and **zero API costs**. |
| **Apache Tika & PDFBox / POI** | Content Extraction Suite | **Apache Tika** handles multi-format parsing (PDF, DOCX, TXT, HTML) under a unified interface. **Apache PDFBox** and **Apache POI** carve out embedded screenshots, illustrations, and figures directly from the binary layouts of PDFs and Word documents, enabling our multimodal-like pipeline. |

---

## 🌟 Key Features

* **Visual RAG Pipeline**: Ingests PDFs and DOCXs, automatically carves out embedded screenshots, and showcases them in a gorgeous lightbox gallery directly below corresponding AI answers.
* **Strict Anti-Hallucination Guard**: Configured with a rigorous `0.4` cosine similarity threshold and detailed system prompts, ensuring the model immediately refuses to answer if the ground-truth context is missing from your files.
* **Persistent Knowledge Base**: Full drag-and-drop support for multi-document ingestion with real-time indexing status trackers and persistent memory.
* **Premium UX/UI**: Designed using a gorgeous dark glassmorphism layout, featuring rich micro-animations, a clean responsive sidebar, and custom ReactMarkdown rendering.

---

## 🚀 Setup & Execution

### 1. Download Local AI Models
Open a terminal on your host machine and run the following commands to pull the necessary models via Ollama:
```bash
# Pull the instruction-tuned chat model
ollama pull phi3:mini

# Pull the text embedding model
ollama pull nomic-embed-text
```

### 2. Launch the Application Stack
From the project root directory, run Docker Compose to build and spin up the frontend, backend, and Qdrant container:
```bash
docker-compose up --build
```

### 3. Access Services
* **Web App UI**: `http://localhost:5173/` (or port 80 if running production Nginx)
* **Backend API**: `http://localhost:8080/`
* **Qdrant DB Console**: `http://localhost:6333/dashboard`

---

## 📁 Repository Structure

```text
rag-assistant/
├── backend/            # Spring Boot 3 Java Service
│   ├── src/            # Document parsing, image extraction, and Spring AI logic
│   └── pom.xml         # Maven dependencies
├── frontend/           # React 18 + Vite Web Application
│   ├── src/            # App layout, ChatWindow, and FileUpload components
│   └── package.json    # Node scripts and dependencies
├── uploads/            # Volumed local image store (gitignored)
├── qdrant_data/        # Persistent database storage (gitignored)
└── docker-compose.yml  # Docker multi-container orchestrator
```

# rag-assistant
