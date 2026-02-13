package com.mkpro.tools;

import com.google.adk.tools.BaseTool;
import com.google.adk.tools.ToolContext;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.Schema;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.reactivex.rxjava3.core.Single;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public class BackgroundJobTools {
    public static BaseTool createListBackgroundJobsTool() {
        return new BaseTool(
                "list_background_jobs",
                "Lists all active background jobs started by the SysAdmin."
        ) {
            @Override
            public Optional<FunctionDeclaration> declaration() {
                return Optional.of(FunctionDeclaration.builder()
                        .name(name())
                        .description(description())
                        .parameters(Schema.builder()
                                .type("OBJECT")
                                .properties(Collections.emptyMap())
                                .build())
                        .build());
            }

            @Override
            public Single<Map<String, Object>> runAsync(Map<String, Object> args, ToolContext toolContext) {
                return Single.fromCallable(() -> Collections.singletonMap("result", ProcessManager.listJobs()));
            }
        };
    }

    public static BaseTool createKillBackgroundJobTool() {
        return new BaseTool(
                "kill_background_job",
                "Terminates a background job by its ID."
        ) {
            @Override
            public Optional<FunctionDeclaration> declaration() {
                return Optional.of(FunctionDeclaration.builder()
                        .name(name())
                        .description(description())
                        .parameters(Schema.builder()
                                .type("OBJECT")
                                .properties(ImmutableMap.of(
                                        "job_id", Schema.builder()
                                                .type("STRING")
                                                .description("The ID of the job to kill.")
                                                .build()
                                ))
                                .required(ImmutableList.of("job_id"))
                                .build())
                        .build());
            }

            @Override
            public Single<Map<String, Object>> runAsync(Map<String, Object> args, ToolContext toolContext) {
                String jobId = (String) args.get("job_id");
                return Single.fromCallable(() -> Collections.singletonMap("result", ProcessManager.killJob(jobId)));
            }
        };
    }
}
