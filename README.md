# mkpro - AI Coding & Research Assistant

`mkpro` is a powerful CLI-based AI assistant built using the Google Agent Development Kit (ADK). It allows you to chat with Gemini directly from your terminal while giving the AI access to your local project files and the internet for research.

## Features

- 📂 **Local File Access**: The agent can read files, list directories, and now **write files** (`write_file`) to modify your codebase.
- 💻 **Shell Execution**: Run shell commands (`run_shell`) directly (e.g., git commands, builds).
- 📝 **Action Logging**: All user interactions and agent responses are logged locally using MapDB for history and auditability.
- 📜 **Log Retrieval**: The agent can retrieve past interaction logs using the `get_action_logs` tool.
- 🌐 **Web Research**: Perform Google searches using built-in grounding.
- 🔗 **URL Lookup**: Fetch and extract text from specific URLs (documentation, blogs, etc.) using the `fetch_url` tool.
- 🚀 **Native Executable**: Runs as a standalone `.exe` on Windows (via Launch4j).

## Prerequisites

- **Java 17+**: Required for building and running.
- **Maven**: Required for building.
- **Google API Key**: You need a valid API key for Google Gemini.

## Setup

1. **Set your API Key**:
   - **Windows (PowerShell)**: `$env:GOOGLE_API_KEY="your_api_key_here"`
   - **Windows (CMD)**: `set GOOGLE_API_KEY=your_api_key_here`

## Building

To build the fat JAR and the native `.exe`:

```bash
mvn clean package
```

The output will be generated in the `target/` directory:
- `target/mkpro-1.4-SNAPSHOT.jar` (Fat JAR)
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

## Usage Examples

Once the `> ` prompt appears, you can try:

- **Analyze Code**: "Read src/main/java/com/mkpro/MkPro.java and explain how the tools are registered."
- **Modify Code**: "Add a comment to the main method in MkPro.java explaining it's the entry point." (The agent will use `git` to save state before modifying).
- **Project Overview**: "List the files in the current directory."
- **Check History**: "Show me the logs of what we've done so far."
- **Research**: "Search for the latest features in Gemini 2.0."
- **Read Documentation**: "Read this page and summarize the main points: https://google.github.io/adk-docs/"

## Maintenance

The agent configuration is located in `src/main/java/com/mkpro/MkPro.java`. You can modify the system instructions or add new tools there. Logs are stored in `mkpro_logs.db`.
