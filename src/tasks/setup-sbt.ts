import toolLib = require('azure-pipelines-tool-lib/tool');
import taskLib = require('azure-pipelines-task-lib/task');
import fs = require('fs');
import {Platform, TaskResult} from "azure-pipelines-task-lib";
import * as path from 'path';

async function run(sbtVersion: string) {
    try {
        if (toolLib.findLocalToolVersions("sbt").includes(sbtVersion)) {
            const resolvedPath = toolLib.findLocalTool("sbt", sbtVersion);
            taskLib.debug(`Using locally cached sbt ${sbtVersion} from ${resolvedPath}.`);
            const binDir = path.join(resolvedPath, 'bin');
            taskLib.debug(`Resolved sbt bin directory at ${binDir}. Contents: ${fs.readdirSync(binDir).join(", ")}`);
            taskLib.prependPath(binDir);
        } else {
            const isWindows: boolean = taskLib.getPlatform() == Platform.Windows;
            const extension: string = isWindows ? 'zip' : 'tgz';
            const downloadUrl = `https://github.com/sbt/sbt/releases/download/v${sbtVersion}/sbt-${sbtVersion}.${extension}`;

            taskLib.debug(`Downloading sbt launcher from: ${downloadUrl}`);
            const downloaded = await toolLib.downloadTool(downloadUrl);
            taskLib.debug(`Downloaded sbt launcher to: ${downloaded}`);

            const extracted: string = isWindows ? await toolLib.extractZip(downloaded) : await toolLib.extractTar(downloaded);
            taskLib.debug(`Extracted sbt launcher to: ${extracted}`);

            const toolPath = path.join(extracted, 'sbt');
            const cached = await toolLib.cacheDir(toolPath, "sbt", sbtVersion);
            taskLib.debug(`Cached sbt launcher ${sbtVersion} locally at ${cached}. Contents: ${fs.readdirSync(cached).join(", ")}`);

            await run(sbtVersion);
        }

    } catch (err) {
        taskLib.setResult(TaskResult.Failed, `Error installing sbt ${sbtVersion}. ${err}`);
    }

}

run(taskLib.getInput("sbtVersion", true)!);
