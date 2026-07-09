# Code Refine AI

An AI-powered code refinement application that uses Gemini and Mistral APIs to analyze and improve your code.

## Project Structure

```text
code-refine-ai/
├── src/
│   ├── main/
│   │   ├── java/
│   │   ├── resources/
│   │   └── static/
│   └── test/
├── pom.xml
├── .env.example
└── README.md
```

## Setup & Installation

### 1. Install Dependencies

```bash
mvn clean install
```

### 2. Configure Environment Variables

Copy `.env.example` to `.env` and add your API keys:

```env
GEMINI_API_KEY=your_gemini_key_here
MISTRAL_API_KEY=your_mistral_key_here
JWT_SECRET=your_secret_key
SERVER_PORT=8080
```

Get your API keys from:

- Gemini: https://aistudio.google.com/apikey
- Mistral: https://console.mistral.ai/api-keys/

### 3. Start the Backend Server

```bash
mvn spring-boot:run
```

The server will start at: http://localhost:8080

## Features

- Code analysis and refinement in multiple languages
- Dual AI models with Gemini and Mistral fallback
- Session management and refinement history
- JWT-based authentication
- Web interface served by Spring Boot
