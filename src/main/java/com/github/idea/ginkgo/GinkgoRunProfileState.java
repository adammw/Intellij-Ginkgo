package com.github.idea.ginkgo;

import com.goide.GoEnvironmentUtil;
import com.goide.sdk.GoSdkService;
import com.goide.sdk.GoSdkUtil;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.process.KillableColoredProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.containers.ContainerUtil;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;


public class GinkgoRunProfileState implements RunProfileState {
    private final ExecutionEnvironment environment;
    private final Project project;
    private final GinkgoRunConfiguration configuration;
    private final Executor executor;

    public GinkgoRunProfileState(@NotNull ExecutionEnvironment env, @Nullable Project project, @NotNull Executor executor, @NotNull GinkgoRunConfiguration configuration) {
        this.environment = env;
        this.project = project;
        this.configuration = configuration;
        this.executor = executor;
    }

    @Override
    public @Nullable ExecutionResult execute(@NotNull Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
        return execute(executor, runner, startProcess());
    }

    public ExecutionResult execute(@NotNull Executor executor, @NotNull ProgramRunner runner, @NotNull ProcessHandler processHandler) {
        GinkgoConsoleProperties consoleProperties = new GinkgoConsoleProperties(configuration, "Ginkgo", executor);
        SMTRunnerConsoleView consoleView = new SMTRunnerConsoleView(consoleProperties);
        SMTestRunnerConnectionUtil.initConsoleView(consoleView, "Ginkgo");
        consoleView.attachToProcess(processHandler);

        return new DefaultExecutionResult(consoleView, processHandler);
    }

    @NotNull
    public ProcessHandler startProcess() throws ExecutionException {
        GinkgoRunConfigurationOptions runOptions = configuration.getOptions();

        Couple<String> pathEntry = updatePath(EnvironmentUtil.getEnvironmentMap());
        VirtualFile goRoot = GoSdkService.getInstance(project).getSdk(null).getSdkRoot();

        GeneralCommandLine commandLine = createCommandLine(runOptions)
                .withEnvironment(pathEntry.first, pathEntry.second)
                .withEnvironment("GOROOT", goRoot.getPath())
                .withEnvironment(runOptions.getEnvData().getEnvs())
                .withWorkDirectory(runOptions.getWorkingDir())
                .withCharset(StandardCharsets.UTF_8);

        KillableColoredProcessHandler processHandler = new KillableColoredProcessHandler(commandLine) {
            @Override
            public void startNotify() {
                notifyTextAvailable("GOROOT=" + commandLine.getEnvironment().get("GOROOT") + " #gosetup\n", ProcessOutputTypes.SYSTEM);
                notifyTextAvailable("WORKING_DIRECTORY=" + runOptions.getWorkingDir() + " #gosetup\n", ProcessOutputTypes.SYSTEM);
                super.startNotify();
            }
        };
        ProcessTerminatedListener.attach(processHandler);

        return processHandler;
    }

    /**
     * Updates the the environment path with the go bin paths as determined by framework configuration.
     *
     * @param env
     * @return Couple<String>
     */
    private Couple<String> updatePath(Map<String, String> env) {
        Collection<String> paths = new ArrayList<>();
        String goBinPaths = GoSdkUtil.retrieveEnvironmentPathForGo(project, null);
        Couple<String> pathEntry = GoEnvironmentUtil.getPathEntry(env);

        ContainerUtil.addIfNotNull(paths, StringUtil.nullize(goBinPaths, true));
        ContainerUtil.addIfNotNull(paths, StringUtil.nullize(pathEntry.second, true));

        return new Couple<>(pathEntry.first, StringUtil.join(paths, File.pathSeparator));
    }

    private GeneralCommandLine createCommandLine(GinkgoRunConfigurationOptions runOptions) {
        List<String> commandList = new ArrayList<>();
        commandList.add(runOptions.getGinkgoExecutable());
        commandList.add("-v");

        if (StringUtils.isNotEmpty(runOptions.getGinkgoAdditionalOptions())) {
            commandList.add(runOptions.getGinkgoAdditionalOptions());
        }

        switch (runOptions.getGinkgoScope()) {
            case ALL:
                commandList.add("-r");
                break;
            case FOCUS:
                commandList.add(String.format("--focus=%s", runOptions.getFocusTestExpression()));
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + runOptions.getGinkgoScope());
        }
        return new GeneralCommandLine(commandList.stream().toArray(String[]::new));
    }
}
