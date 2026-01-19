# mkpro - AI Coding & Research Assistant

`mkpro` is a powerful CLI-based AI assistant built using the Google Agent Development Kit (ADK). It allows you to chat with LLMs (via Ollama or Gemini) directly from your terminal while giving the AI access to your local project files and the internet for research.

## Features

- 📂 **Local File Access**: The agent can read files, list directories, and **write files** (`write_file`) to modify your codebase.
- 💻 **Shell Execution**: Run shell commands (`run_shell`) directly (e.g., git commands, builds).
- 🖼️ **Image Analysis**: Analyze local image files by referencing them in your prompt.
- 📝 **Action Logging**: All user interactions and agent responses are logged locally using MapDB.
- 🔄 **Session Management**: Reset, compact, or summarize your session context on the fly.
- 🤖 **Multi-Model Support**: Switch between local Ollama models dynamically.
- 🌐 **Web Research**: Perform searches and fetch URL content.
- 🚀 **Native Executable**: Runs as a standalone `.exe` on Windows (via Launch4j).

## Prerequisites

- **Java 17+**: Required for building and running.
- **Maven**: Required for building.
- **Ollama**: Required for running local models. Ensure it is running on `http://localhost:11434`.
- **Google API Key**: (Optional if using Gemini models) Set `GOOGLE_API_KEY`.

## Setup

1. **Set your API Key** (if needed):
   - **Windows (PowerShell)**: `$env:GOOGLE_API_KEY="your_api_key_here"`
   - **Windows (CMD)**: `set GOOGLE_API_KEY=your_api_key_here`

2. **Start Ollama**:
   Ensure your local Ollama instance is running.

## Building

To build the fat JAR and the native `.exe`:

```bash
mvn clean package
```

The output will be generated in the `target/` directory:
- `target/mkpro-1.4-SNAPSHOT-shaded.jar` (Fat JAR)
- `target/mkpro.exe` (Native Windows Executable)

## Running

### Using the Executable (Recommended)
```bash
./target/mkpro.exe
```

### Using Java
```bash
java -jar target/mkpro-1.4-SNAPSHOT-shaded.jar
```

## Commands

Inside the application, you can use the following commands:

- **/help** (or **/h**): Display the list of available commands.
- **/reset**: Clear the current session memory and start fresh.
- **/compact**: Summarize the current conversation history and start a new session with that summary as context (saves context window).
- **/summarize**: Ask the agent to generate a detailed summary of the session into `session_summary.txt`.
- **/models**: List all available local Ollama models.
- **/model**: Switch the active LLM model interactively.
- **exit**: Quit the application.

## Usage Examples

Once the `> ` prompt appears, you can try:

- **Analyze Code**: "Read src/main/java/com/mkpro/MkPro.java and explain how the tools are registered."
- **Modify Code**: "Add a comment to the main method in MkPro.java explaining it's the entry point." (The agent will use `git` to save state before modifying).
- **Image Input**: "What is this image? diagram.png" (Ensure the file path is correct).
- **Project Overview**: "List the files in the current directory."
- **Research**: "Read this page and summarize the main points: https://google.github.io/adk-docs/"
- **Manage Context**: If the conversation gets too long, type `/compact` to tidy up.

## Maintenance

The agent configuration is located in `src/main/java/com/mkpro/MkPro.java`. You can modify the system instructions or add new tools there. Logs are stored in `mkpro_logs.db`.