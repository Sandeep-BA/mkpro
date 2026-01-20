# mkpro - AI Coding & Research Assistant

`mkpro` is a sophisticated CLI-based AI assistant built using the Google Agent Development Kit (ADK). It features a modular multi-agent architecture and supports local models (via Ollama) and cloud models (via Gemini API and AWS Bedrock).

## Features

- 🤖 **Expanded Multi-Agent Team**:
    - **Coordinator**: Orchestrates the workflow, performs research, and manages long-term memory.
    - **Coder**: Specialized in reading/writing files and analyzing project structure.
    - **SysAdmin**: Handles shell command execution and system-level tasks.
    - **Tester**: Dedicated to writing and running unit tests to ensure code quality.
    - **DocWriter**: specialized in writing, updating, and maintaining project documentation.
- ⚙️ **Granular & Persistent Configuration**: Configure each agent independently (e.g., use Claude for coding, Gemini for docs, and Llama 3 for coordination). Settings are saved to `~/.mkpro/central_memory.db` and persist across sessions.
- 🏢 **Central Memory**: Persist project summaries and agent configurations across sessions.
- 🌐 **Multi-Provider Support**: Seamlessly switch between **Ollama** (local), **Gemini** (Google Cloud), and **Bedrock** (AWS) providers.
- 📂 **Local File Access**: Full capability to read and modify your codebase safely.
- 💻 **Shell Execution**: Run shell commands directly with automatic state saving via Git.
- 🖼️ **Image Analysis**: Analyze local image files by referencing them in your prompts.
- 📅 **Context Aware**: Agents are automatically aware of the current date and working directory.

## Prerequisites

- **Java 17+**: Required for building and running.
- **Maven**: Required for building.
- **Ollama**: (Optional) For running local models. Ensure it is running on `http://localhost:11434`.
- **Google API Key**: (Optional) Required for Gemini models. Set the `GOOGLE_API_KEY` environment variable.
- **AWS Credentials**: (Optional) Required for Bedrock models. Ensure your environment is configured with AWS credentials (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`) and a default region (`AWS_REGION`).

## Setup

1. **Configure Providers**:
   - **Gemini**: `set GOOGLE_API_KEY=your_api_key`
   - **Bedrock**: Use `aws configure` or set `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, and `AWS_REGION`.
   - **Ollama**: Ensure the local daemon is running.

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

Inside the CLI, you can use the following commands:

- **/help** (or **/h**): Display available commands.
- **/config**: **(New)** Interactive menu to configure the provider and model for any agent. Can also be used as `/config <Agent> <Provider> <Model>`.
- **/status**: **(New)** Show a detailed table of all agent configurations and memory system status.
- **/provider**: Quick switch for the **Coordinator**'s provider.
- **/models**: List models available for the Coordinator's active provider.
- **/model**: Quick switch for the **Coordinator**'s model.
- **/init**: Initialize project memory in the central database.
- **/re-init**: Refresh the project summary in the central database.
- **/remember**: Manually trigger a project analysis and save to central memory.
- **/compact**: Summarize current history and start a fresh session (saves tokens).
- **/reset**: Clear the current session memory entirely.
- **/summarize**: Export a session summary to `session_summary.txt`.
- **exit**: Quit the application.

## Usage Examples

Once the `> ` prompt appears, you can try:

- **Configure Team**: Type `/config` to interactively set the *Coder* to use `GEMINI` and the *SysAdmin* to use `OLLAMA`.
- **Initialize Project**: `/init` (Let the agents learn your project structure).
- **Coding Task**: "Add a logger to the main method in MkPro.java." (Delegates to Coder).
- **Testing Task**: "Write a unit test for the new logger." (Delegates to Tester).
- **Documentation**: "Update the README to include the new features." (Delegates to DocWriter).
- **Check Status**: Type `/status` to see your team's configuration.

## Maintenance

The project is now modularized:
- `com.mkpro.MkPro`: Main entry point and command loop.
- `com.mkpro.agents`: Agent management and delegation logic.
- `com.mkpro.tools`: Tool definitions.
- `com.mkpro.models`: Configuration data classes.
- `mkpro_logs.db`: Interaction logs.
- `~/.mkpro/central_memory.db`: Persistent memory and configuration storage.
