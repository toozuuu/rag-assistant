# Contributing to AI-Powered RAG Assistant

First off, thank you for taking the time to contribute! Contributions are what make the open-source community such an amazing place to learn, inspire, and create. 

Any contributions you make are **highly appreciated**. Please read the guidelines below to ensure a smooth and effective collaboration.

---

## Code of Conduct
This project and everyone participating in it is governed by the [Code of Conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code. Please report unacceptable behavior to the project maintainer.

---

## How Can I Contribute?

### 1. Reporting Bugs
* Check the [GitHub Issues](https://github.com/toozuuu/rag-assistant/issues) tab to ensure the bug hasn't already been reported.
* If it's a new issue, open a bug report and include:
  * A clear, descriptive title.
  * Precise steps to reproduce the issue.
  * Your operating system, Java version, Node.js version, and browser.
  * Relevant logs or screenshots.

### 2. Suggesting Enhancements
* Open a new issue outlining the feature or enhancement.
* Explain *why* this feature would be useful and how it aligns with the project goals.
* Provide mockups or code snippets if applicable.

### 3. Submitting Pull Requests (PRs)
* **Fork** the repository and create your branch from `main`.
* If you've added code that should be tested, add corresponding tests (unit or integration).
* Ensure the build passes locally (both Spring Boot and React Vite).
* Format your code cleanly (see style standards below).
* Open the PR targeting the `main` branch of the original repository.
* Write a clear, detailed PR description explaining the changes.

---

## Local Development Setup

Refer to the main [README.md](README.md) for quick-start Docker instructions. For active development, it is highly recommended to run the services natively:

### 1. Prerequisites
* **Java**: JDK 21 or higher (Java 25 recommended)
* **Node.js**: v18 or higher (v25 recommended)
* **Docker**: Required to run the local Qdrant database (and optionally Ollama)
* **Ollama**: Installed and running locally

### 2. Run the Vector Database (Qdrant)
Run a local instance of Qdrant via Docker:
```bash
docker run -d -p 6333:6333 -p 6334:6334 -v qdrant_storage:/qdrant/storage qdrant/qdrant
```

### 3. Backend Setup (Spring Boot)
1. Navigate to the backend directory:
   ```bash
   cd backend
   ```
2. Copy `.env.example` to `.env` and fill in your API keys (if not using local Ollama model paths):
   ```bash
   cp .env.example .env
   ```
3. Run the Spring Boot application using Maven:
   ```bash
   mvnw spring-boot:run
   ```
   *The backend will run on `http://localhost:8080`.*

### 4. Frontend Setup (React & Vite)
1. Navigate to the frontend directory:
   ```bash
   cd ../frontend
   ```
2. Install dependencies:
   ```bash
   npm install
   ```
3. Start the Vite development server:
   ```bash
   npm run dev
   ```
   *The frontend web app will run on `http://localhost:5173`.*

---

## Development & Style Guidelines

### Git Branching
Always create a new branch for your work instead of committing directly to `main`:
* Features: `feature/your-feature-name`
* Bug Fixes: `bugfix/your-fix-name`
* Documentation: `docs/doc-update-name`
* Refactoring: `refactor/change-name`

### Commit Message Conventions
We follow the **Conventional Commits** specification:
* `feat: ...` for a new feature.
* `fix: ...` for a bug fix.
* `docs: ...` for documentation changes.
* `style: ...` for formatting, semicolons, etc. (no production code changes).
* `refactor: ...` for code changes that neither fix a bug nor add a feature.
* `test: ...` for adding or correcting tests.
* `chore: ...` for updating build scripts, packages, configs, etc.

Example:
```bash
feat: add dark mode toggle to ChatWindow component
```

### Code Formatting
* **Java Backend**: Follow standard Java coding conventions. Keep classes focused, leverage Spring Boot's dependency injection properly, and avoid hardcoded settings.
* **React Frontend**: Keep components focused, leverage custom hooks for logic separation, and use clean, modular CSS modules or styles. Always run `npm run lint` before committing to catch formatting and syntax errors.
