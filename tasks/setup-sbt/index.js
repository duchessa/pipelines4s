"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    Object.defineProperty(o, k2, { enumerable: true, get: function() { return m[k]; } });
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || function (mod) {
    if (mod && mod.__esModule) return mod;
    var result = {};
    if (mod != null) for (var k in mod) if (k !== "default" && Object.prototype.hasOwnProperty.call(mod, k)) __createBinding(result, mod, k);
    __setModuleDefault(result, mod);
    return result;
};
var __awaiter = (this && this.__awaiter) || function (thisArg, _arguments, P, generator) {
    function adopt(value) { return value instanceof P ? value : new P(function (resolve) { resolve(value); }); }
    return new (P || (P = Promise))(function (resolve, reject) {
        function fulfilled(value) { try { step(generator.next(value)); } catch (e) { reject(e); } }
        function rejected(value) { try { step(generator["throw"](value)); } catch (e) { reject(e); } }
        function step(result) { result.done ? resolve(result.value) : adopt(result.value).then(fulfilled, rejected); }
        step((generator = generator.apply(thisArg, _arguments || [])).next());
    });
};
Object.defineProperty(exports, "__esModule", { value: true });
const toolLib = require("azure-pipelines-tool-lib/tool");
const taskLib = require("azure-pipelines-task-lib/task");
const fs = require("fs");
const azure_pipelines_task_lib_1 = require("azure-pipelines-task-lib");
const path = __importStar(require("path"));
function run(sbtVersion) {
    return __awaiter(this, void 0, void 0, function* () {
        try {
            if (toolLib.findLocalToolVersions("sbt").includes(sbtVersion)) {
                const resolvedPath = toolLib.findLocalTool("sbt", sbtVersion);
                taskLib.debug(`Using locally cached sbt ${sbtVersion} from ${resolvedPath}.`);
                const binDir = path.join(resolvedPath, 'bin');
                taskLib.debug(`Resolved sbt bin directory at ${binDir}. Contents: ${fs.readdirSync(binDir).join(", ")}`);
                taskLib.prependPath(binDir);
            }
            else {
                const isWindows = taskLib.getPlatform() == azure_pipelines_task_lib_1.Platform.Windows;
                const extension = isWindows ? 'zip' : 'tgz';
                const downloadUrl = `https://github.com/sbt/sbt/releases/download/v${sbtVersion}/sbt-${sbtVersion}.${extension}`;
                taskLib.debug(`Downloading sbt launcher from: ${downloadUrl}`);
                const downloaded = yield toolLib.downloadTool(downloadUrl);
                taskLib.debug(`Downloaded sbt launcher to: ${downloaded}`);
                const extracted = isWindows ? yield toolLib.extractZip(downloaded) : yield toolLib.extractTar(downloaded);
                taskLib.debug(`Extracted sbt launcher to: ${extracted}`);
                const toolPath = path.join(extracted, 'sbt');
                const cached = yield toolLib.cacheDir(toolPath, "sbt", sbtVersion);
                taskLib.debug(`Cached sbt launcher ${sbtVersion} locally at ${cached}. Contents: ${fs.readdirSync(cached).join(", ")}`);
                yield run(sbtVersion);
            }
        }
        catch (err) {
            taskLib.setResult(azure_pipelines_task_lib_1.TaskResult.Failed, `Error installing sbt ${sbtVersion}. ${err}`);
        }
    });
}
run(taskLib.getInput("sbtVersion", true));
